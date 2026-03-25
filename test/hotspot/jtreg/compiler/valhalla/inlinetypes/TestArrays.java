/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;

import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_ARRAY_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.CHECKCAST_ARRAYCOPY;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.CLONE_INTRINSIC_SLOW_PATH;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.INLINE_ARRAY_NULL_GUARD;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.JLONG_ARRAYCOPY;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.LOAD_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.LOAD_UNKNOWN_INLINE;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_UNKNOWN_INLINE;
import static compiler.valhalla.inlinetypes.InlineTypes.*;

import static compiler.lib.ir_framework.IRNode.ALLOC;
import static compiler.lib.ir_framework.IRNode.ALLOC_ARRAY;
import static compiler.lib.ir_framework.IRNode.CLASS_CHECK_TRAP;
import static compiler.lib.ir_framework.IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP;
import static compiler.lib.ir_framework.IRNode.LOOP;
import static compiler.lib.ir_framework.IRNode.PREDICATE_TRAP;
import static compiler.lib.ir_framework.IRNode.UNSTABLE_IF_TRAP;

/*
 * @test
 * @key randomness
 * @summary Test value class arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run main compiler.valhalla.inlinetypes.TestArrays 0
 */

/*
 * @test
 * @key randomness
 * @summary Test value class arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run main compiler.valhalla.inlinetypes.TestArrays 1
 */

/*
 * @test
 * @key randomness
 * @summary Test value class arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run main compiler.valhalla.inlinetypes.TestArrays 2
 */

/*
 * @test
 * @key randomness
 * @summary Test value class arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run main compiler.valhalla.inlinetypes.TestArrays 3
 */

/*
 * @test
 * @key randomness
 * @summary Test value class arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run main compiler.valhalla.inlinetypes.TestArrays 4
 */

/*
 * @test
 * @key randomness
 * @summary Test value class arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run main compiler.valhalla.inlinetypes.TestArrays 5
 */

/*
 * @test
 * @key randomness
 * @summary Test value class arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run main compiler.valhalla.inlinetypes.TestArrays 6
 */

@ForceCompileClassInitializer
public class TestArrays {

    public static void main(String[] args) {
        Scenario[] scenarios = InlineTypes.DEFAULT_SCENARIOS;
        scenarios[2].addFlags("--enable-preview", "-XX:-MonomorphicArrayCheck", "-XX:-UncommonNullCast", "-XX:+StressArrayCopyMacroNode");
        scenarios[3].addFlags("--enable-preview", "-XX:-MonomorphicArrayCheck", "-XX:+UseArrayFlattening", "-XX:-UncommonNullCast");
        scenarios[4].addFlags("--enable-preview", "-XX:-MonomorphicArrayCheck", "-XX:-UncommonNullCast");
        scenarios[5].addFlags("--enable-preview", "-XX:-MonomorphicArrayCheck", "-XX:-UncommonNullCast", "-XX:+StressArrayCopyMacroNode");

        InlineTypes.getFramework()
                   .addScenarios(scenarios[Integer.parseInt(args[0])])
                   .addHelperClasses(MyValue1.class, MyValue2.class, MyValue2Inline.class)
                   .start();
    }

    static {
        // Make sure RuntimeException is loaded to prevent uncommon traps in IR verified tests
        RuntimeException tmp = new RuntimeException("42");
    }

    // Helper methods and classes

    protected long hash() {
        return hash(rI, rL);
    }

    protected long hash(int x, long y) {
        return MyValue1.createWithFieldsInline(x, y).hash();
    }

    static void verify(Object[] src, Object[] dst) {
        for (int i = 0; i < src.length; ++i) {
            Asserts.assertEQ(src[i], dst[i]);
        }
    }

    static boolean compile_and_run_again_if_deoptimized(RunInfo info) {
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            if (TestFramework.isCompiled(m)) {
                TestFramework.compile(m, CompLevel.C2);
            }
        }
        return false;
    }

    @LooselyConsistentValue
    static value class NotFlattenable {
        private final Object o1 = null;
        private final Object o2 = null;
        private final Object o3 = null;
        private final Object o4 = null;
        private final Object o5 = null;
        private final Object o6 = null;

        static final NotFlattenable DEFAULT = new NotFlattenable();
    }

    // Test value class array creation and initialization
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {ALLOC_ARRAY_OF_MYVALUE_KLASS, "= 1"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        counts = {ALLOC_ARRAY_OF_MYVALUE_KLASS, "= 1"},
        failOn = {LOAD_OF_ANY_KLASS})
    public MyValue1[] test1(int len) {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsDontInline(rI, rL);
        }
        return va;
    }

    @Run(test = "test1")
    public void test1_verifier() {
        int len = Math.abs(rI % 10);
        MyValue1[] va = test1(len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(va[i].hash(), hash());
        }
    }

    // Test creation of a value class array and element access
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test2() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        va[0] = MyValue1.createWithFieldsInline(rI, rL);
        return va[0].hash();
    }

    @Run(test = "test2")
    public void test2_verifier() {
        long result = test2();
        Asserts.assertEQ(result, hash());
    }

    // Test receiving a value class array from the interpreter,
    // updating its elements in a loop and computing a hash.
    @Test
    @IR(failOn = {ALLOC_ARRAY_OF_MYVALUE_KLASS})
    public long test3(MyValue1[] va) {
        long result = 0;
        for (int i = 0; i < 10; ++i) {
            result += va[i].hash();
            va[i] = MyValue1.createWithFieldsInline(rI + 1, rL + 1);
        }
        return result;
    }

    @Run(test = "test3")
    public void test3_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 10, MyValue1.DEFAULT);
        long expected = 0;
        for (int i = 0; i < 10; ++i) {
            va[i] = MyValue1.createWithFieldsDontInline(rI + i, rL + i);
            expected += va[i].hash();
        }
        long result = test3(va);
        Asserts.assertEQ(expected, result);
        for (int i = 0; i < 10; ++i) {
            if (va[i].hash() != hash(rI + 1, rL + 1)) {
                Asserts.assertEQ(va[i].hash(), hash(rI + 1, rL + 1));
            }
        }
    }

    // Test returning a value class array received from the interpreter
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue1[] test4(MyValue1[] va) {
        return va;
    }

    @Run(test = "test4")
    public void test4_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 10, MyValue1.DEFAULT);
        for (int i = 0; i < 10; ++i) {
            va[i] = MyValue1.createWithFieldsDontInline(rI + i, rL + i);
        }
        va = test4(va);
        for (int i = 0; i < 10; ++i) {
            Asserts.assertEQ(va[i].hash(), hash(rI + i, rL + i));
        }
    }

    // Merge value class arrays created from two branches
    @Test
    public MyValue1[] test5(boolean b) {
        MyValue1[] va;
        if (b) {
            va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 5, MyValue1.DEFAULT);
            for (int i = 0; i < 5; ++i) {
                va[i] = MyValue1.createWithFieldsInline(rI, rL);
            }
        } else {
            va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 10, MyValue1.DEFAULT);
            for (int i = 0; i < 10; ++i) {
                va[i] = MyValue1.createWithFieldsInline(rI + i, rL + i);
            }
        }
        long sum = va[0].hashInterpreted();
        if (b) {
            va[0] = MyValue1.createWithFieldsDontInline(rI, sum);
        } else {
            va[0] = MyValue1.createWithFieldsDontInline(rI + 1, sum + 1);
        }
        return va;
    }

    @Run(test = "test5")
    public void test5_verifier() {
        MyValue1[] va = test5(true);
        Asserts.assertEQ(va.length, 5);
        Asserts.assertEQ(va[0].hash(), hash(rI, hash()));
        for (int i = 1; i < 5; ++i) {
            Asserts.assertEQ(va[i].hash(), hash());
        }
        va = test5(false);
        Asserts.assertEQ(va.length, 10);
        Asserts.assertEQ(va[0].hash(), hash(rI + 1, hash(rI, rL) + 1));
        for (int i = 1; i < 10; ++i) {
            Asserts.assertEQ(va[i].hash(), hash(rI + i, rL + i));
        }
    }

    // Test creation of value class array with single element
    @Test
    @IR(failOn = {ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue1 test6() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        return va[0];
    }

    @Run(test = "test6")
    public void test6_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        MyValue1 v = test6();
        Asserts.assertEQ(v.hashPrimitive(), va[0].hashPrimitive());
    }

    // Test initialization of value class arrays
    @Test
    @IR(failOn = {LOAD_OF_ANY_KLASS})
    public MyValue1[] test7(int len) {
        return (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
    }

    @Run(test = "test7")
    public void test7_verifier() {
        int len = Math.abs(rI % 10);
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] var = test7(len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(va[i].hashPrimitive(), var[i].hashPrimitive());
        }
    }

    // Test creation of value class array with zero length
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue1[] test8() {
        return (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 0, MyValue1.DEFAULT);
    }

    @Run(test = "test8")
    public void test8_verifier() {
        MyValue1[] va = test8();
        Asserts.assertEQ(va.length, 0);
    }

    static MyValue1[] test9_va;

    // Test that value class array loaded from field has correct type
    @Test
    @IR(failOn = LOOP)
    public long test9() {
        return test9_va[0].hash();
    }

    @Run(test = "test9")
    public void test9_verifier() {
        test9_va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        test9_va[0] = MyValue1.createWithFieldsInline(rI, rL);
        long result = test9();
        Asserts.assertEQ(result, hash());
    }

    // Multi-dimensional arrays
    @Test
    public MyValue1[][][] test10(int len1, int len2, int len3) {
        MyValue1[][][] arr = new MyValue1[len1][len2][len3];
        for (int i = 0; i < len1; i++) {
            for (int j = 0; j < len2; j++) {
                for (int k = 0; k < len3; k++) {
                    arr[i][j][k] = MyValue1.createWithFieldsDontInline(rI + i , rL + j + k);
                }
            }
        }
        return arr;
    }

    @Run(test = "test10")
    public void test10_verifier() {
        MyValue1[][][] arr = test10(2, 3, 4);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 4; k++) {
                    Asserts.assertEQ(MyValue1.createWithFieldsDontInline(rI + i , rL + j + k), arr[i][j][k]);
                }
            }
        }
    }

    @Test
    public void test11(MyValue1[][][] arr, long[] res) {
        int l = 0;
        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr[i].length; j++) {
                for (int k = 0; k < arr[i][j].length; k++) {
                    res[l] = arr[i][j][k].hash();
                    l++;
                }
            }
        }
    }

    @Run(test = "test11")
    public void test11_verifier() {
        MyValue1[][][] arr = new MyValue1[2][3][4];
        long[] res = new long[2*3*4];
        long[] verif = new long[2*3*4];
        int l = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 4; k++) {
                    arr[i][j][k] = MyValue1.createWithFieldsDontInline(rI + i, rL + j + k);
                    verif[l] = arr[i][j][k].hash();
                    l++;
                }
            }
        }
        test11(arr, res);
        for (int i = 0; i < verif.length; i++) {
            Asserts.assertEQ(res[i], verif[i]);
        }
    }

    // Array load out of bounds (upper bound) at compile time
    @Test
    public int test12() {
        int arraySize = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, arraySize, MyValue1.DEFAULT);

        for (int i = 0; i < arraySize; i++) {
            va[i] = MyValue1.createWithFieldsDontInline(rI + 1, rL);
        }

        try {
            return va[arraySize + 1].x;
        } catch (ArrayIndexOutOfBoundsException e) {
            return rI;
        }
    }

    @Run(test = "test12")
    public void test12_verifier() {
        Asserts.assertEQ(test12(), rI);
    }

    // Array load  out of bounds (lower bound) at compile time
    @Test
    public int test13() {
        int arraySize = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, arraySize, MyValue1.DEFAULT);

        for (int i = 0; i < arraySize; i++) {
            va[i] = MyValue1.createWithFieldsDontInline(rI + i, rL);
        }

        try {
            return va[-arraySize].x;
        } catch (ArrayIndexOutOfBoundsException e) {
            return rI;
        }
    }

    @Run(test = "test13")
    public void test13_verifier() {
        Asserts.assertEQ(test13(), rI);
    }

    // Array load out of bound not known to compiler (both lower and upper bound)
    @Test
    public int test14(MyValue1[] va, int index)  {
        return va[index].x;
    }

    @Run(test = "test14")
    public void test14_verifier() {
        int arraySize = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, arraySize, MyValue1.DEFAULT);

        for (int i = 0; i < arraySize; i++) {
            va[i] = MyValue1.createWithFieldsDontInline(rI, rL);
        }

        int result;
        for (int i = -20; i < 20; i++) {
            try {
                result = test14(va, i);
            } catch (ArrayIndexOutOfBoundsException e) {
                result = rI;
            }
            Asserts.assertEQ(result, rI);
        }
    }

    // Array store out of bounds (upper bound) at compile time
    @Test
    public int test15() {
        int arraySize = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, arraySize, MyValue1.DEFAULT);

        try {
            for (int i = 0; i <= arraySize; i++) {
                va[i] = MyValue1.createWithFieldsDontInline(rI + 1, rL);
            }
            return rI - 1;
        } catch (ArrayIndexOutOfBoundsException e) {
            return rI;
        }
    }

    @Run(test = "test15")
    public void test15_verifier() {
        Asserts.assertEQ(test15(), rI);
    }

    // Array store out of bounds (lower bound) at compile time
    @Test
    public int test16() {
        int arraySize = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, arraySize, MyValue1.DEFAULT);

        try {
            for (int i = -1; i <= arraySize; i++) {
                va[i] = MyValue1.createWithFieldsDontInline(rI + 1, rL);
            }
            return rI - 1;
        } catch (ArrayIndexOutOfBoundsException e) {
            return rI;
        }
    }

    @Run(test = "test16")
    public void test16_verifier() {
        Asserts.assertEQ(test16(), rI);
    }

    // Array store out of bound not known to compiler (both lower and upper bound)
    @Test
    public int test17(MyValue1[] va, int index, MyValue1 vt)  {
        va[index] = vt;
        return va[index].x;
    }

    @Run(test = "test17")
    public void test17_verifier() {
        int arraySize = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, arraySize, MyValue1.DEFAULT);

        for (int i = 0; i < arraySize; i++) {
            va[i] = MyValue1.createWithFieldsDontInline(rI, rL);
        }

        MyValue1 vt = MyValue1.createWithFieldsDontInline(rI + 1, rL);
        int result;
        for (int i = -20; i < 20; i++) {
            try {
                result = test17(va, i, vt);
            } catch (ArrayIndexOutOfBoundsException e) {
                result = rI + 1;
            }
            Asserts.assertEQ(result, rI + 1);
        }

        for (int i = 0; i < arraySize; i++) {
            Asserts.assertEQ(va[i].x, rI + 1);
        }
    }

    // clone() as stub call
    @Test
    public MyValue1[] test18(MyValue1[] va) {
        return va.clone();
    }

    @Run(test = "test18")
    public void test18_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        MyValue1[] result = test18(va);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(va[i], result[i]);
        }
    }

    // clone() as series of loads/stores
    static MyValue1[] test19_orig = null;

    @Test
    public MyValue1[] test19() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        for (int i = 0; i < va.length; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        test19_orig = va;

        return va.clone();
    }

    @Run(test = "test19")
    public void test19_verifier() {
        MyValue1[] result = test19();
        for (int i = 0; i < test19_orig.length; ++i) {
            Asserts.assertEQ(test19_orig[i], result[i]);
        }
    }

    // arraycopy() of value class array with oop fields
    @Test
    public void test20(MyValue1[] src, MyValue1[] dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Run(test = "test20")
    public void test20_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] dst = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        test20(src, dst);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(dst[i], src[i]);
        }
    }

    // arraycopy() of value class array with no oop field
    @Test
    public void test21(MyValue2[] src, MyValue2[] dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Run(test = "test21")
    public void test21_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test21(src, dst);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(dst[i], src[i]);
        }
    }

    // arraycopy() of value class array with oop field and tightly
    // coupled allocation as dest
    @Test
    public MyValue1[] test22(MyValue1[] src) {
        MyValue1[] dst = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, src.length, MyValue1.DEFAULT);
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }

    @Run(test = "test22")
    public void test22_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        MyValue1[] dst = test22(src);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(dst[i], src[i]);
        }
    }

    // arraycopy() of value class array with oop fields and tightly
    // coupled allocation as dest
    @Test
    public MyValue1[] test23(MyValue1[] src) {
        MyValue1[] dst = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, src.length + 10, MyValue1.DEFAULT);
        System.arraycopy(src, 0, dst, 5, src.length);
        return dst;
    }

    @Run(test = "test23")
    public void test23_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        MyValue1[] dst = test23(src);
        for (int i = 5; i < len; ++i) {
            Asserts.assertEQ(dst[i], src[i]);
        }
    }

    // arraycopy() of value class array passed as Object
    @Test
    public void test24(MyValue1[] src, Object dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Run(test = "test24")
    public void test24_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] dst1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        Object[] dst2 = new Object[len];
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        test24(src, dst1);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(dst1[i], src[i]);
        }
        test24(src, dst2);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(dst2[i], src[i]);
        }
    }

    // short arraycopy() with no oop field
    @Test
    public void test25(MyValue2[] src, MyValue2[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test25")
    public void test25_verifier() {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test25(src, dst);
        for (int i = 0; i < 8; ++i) {
            Asserts.assertEQ(dst[i], src[i]);
        }
    }

    // short arraycopy() with oop fields
    @Test
    public void test26(MyValue1[] src, MyValue1[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test26")
    public void test26_verifier() {
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] dst = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        test26(src, dst);
        for (int i = 0; i < 8; ++i) {
            Asserts.assertEQ(dst[i], src[i]);
        }
    }

    // short arraycopy() with oop fields and offsets
    @Test
    public void test27(MyValue1[] src, MyValue1[] dst) {
        System.arraycopy(src, 1, dst, 2, 6);
    }

    @Run(test = "test27")
    public void test27_verifier() {
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] dst = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        test27(src, dst);
        for (int i = 2; i < 8; ++i) {
            Asserts.assertEQ(dst[i], src[i - 1]);
        }
    }

    // non escaping allocations
    // TODO 8252027: Make sure this is optimized with ZGC
    @Test
    @IR(applyIf = {"UseZGC", "false"},
        failOn = {ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue2 test28() {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        src[0] = MyValue2.createWithFieldsInline(rI, rD);
        MyValue2[] dst = (MyValue2[])src.clone();
        return dst[0];
    }

    @Run(test = "test28")
    public void test28_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        MyValue2 result = test28();
        Asserts.assertEQ(v, result);
    }

    // non escaping allocations
    // TODO 8350865: Support flat arrays in System.arraycopy
    // @Test(failOn = ALLOC_OF_MYVALUE_KLASS, + ALLOC_ARRAY_OF_MYVALUE_KLASS + LOOP + LOAD_OF_ANY_KLASS + STORE_OF_ANY_KLASS + UNSTABLE_IF_TRAP, PREDICATE_TRAP)
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        failOn = {ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = {ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue2 test29(MyValue2[] src) {
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        System.arraycopy(src, 0, dst, 0, 10);
        return dst[0];
    }

    @Run(test = "test29")
    public void test29_verifier() {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        for (int i = 0; i < 10; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        MyValue2 v = test29(src);
        Asserts.assertEQ(src[0], v);
    }

    // non escaping allocation with uncommon trap that needs
    // eliminated value class array element as debug info
    @Test
    public MyValue2 test30(MyValue2[] src, boolean flag) {
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        System.arraycopy(src, 0, dst, 0, 10);
        if (flag) { }
        return dst[0];
    }

    @Run(test = "test30")
    @Warmup(10000)
    public void test30_verifier(RunInfo info) {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        for (int i = 0; i < 10; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        MyValue2 v = test30(src, !info.isWarmUp());
        Asserts.assertEQ(src[0], v);
    }


    // non escaping allocation with memory phi
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test31(boolean b, boolean deopt, Method m) {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 1, MyValue2.DEFAULT);
        if (b) {
            src[0] = MyValue2.createWithFieldsInline(rI, rD);
        } else {
            src[0] = MyValue2.createWithFieldsInline(rI+1, rD+1);
        }
        if (deopt) {
            // uncommon trap
            TestFramework.deoptimize(m);
        }
        return src[0].hash();
    }

    @Run(test = "test31")
    public void test31_verifier(RunInfo info) {
        MyValue2 v1 = MyValue2.createWithFieldsInline(rI, rD);
        long result1 = test31(true, !info.isWarmUp(), info.getTest());
        Asserts.assertEQ(result1, v1.hash());
        MyValue2 v2 = MyValue2.createWithFieldsInline(rI + 1, rD + 1);
        long result2 = test31(false, !info.isWarmUp(), info.getTest());
        Asserts.assertEQ(result2, v2.hash());
    }

    // Tests with Object arrays and clone/arraycopy
    // clone() as stub call
    @Test
    public Object[] test32(Object[] va) {
        return va.clone();
    }

    @Run(test = "test32")
    public void test32_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        MyValue1[] result = (MyValue1[])test32(va);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(va[i], result[i]);
        }
    }

    @Test
    public Object[] test33(Object[] va) {
        return va.clone();
    }

    @Run(test = "test33")
    public void test33_verifier() {
        int len = Math.abs(rI) % 10;
        Object[] va = new Object[len];
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        Object[] result = test33(va);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(va[i], result[i]);
            // Check that array has correct properties (null-ok)
            result[i] = null;
        }
    }

    // clone() as series of loads/stores
    static Object[] test34_orig = null;

    @ForceInline
    public Object[] test34_helper(boolean flag) {
        Object[] va = null;
        if (flag) {
            va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
            for (int i = 0; i < va.length; ++i) {
                va[i] = MyValue1.createWithFieldsDontInline(rI, rL);
            }
        } else {
            va = new Object[8];
        }
        return va;
    }

    @Test
    public Object[] test34(boolean flag) {
        Object[] va = test34_helper(flag);
        test34_orig = va;
        return va.clone();
    }

    @Run(test = "test34")
    public void test34_verifier(RunInfo info) {
        test34(false);
        for (int i = 0; i < 10; i++) { // make sure we do deopt
            Object[] result = test34(true);
            verify(test34_orig, result);
            // Check that array has correct properties (null-free)
            try {
                result[0] = null;
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
        if (compile_and_run_again_if_deoptimized(info)) {
            Object[] result = test34(true);
            verify(test34_orig, result);
            // Check that array has correct properties (null-free)
            try {
                result[0] = null;
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // arraycopy() of value class array of unknown size
    @Test
    public void test35(Object src, Object dst, int len) {
        System.arraycopy(src, 0, dst, 0, len);
    }

    @Run(test = "test35")
    public void test35_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] dst1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        Object[] dst2 = new Object[len];
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        test35(src, dst1, src.length);
        verify(src, dst1);
        test35(src, dst2, src.length);
        verify(src, dst2);
        if (compile_and_run_again_if_deoptimized(info)) {
            test35(src, dst1, src.length);
            verify(src, dst1);
        }
    }

    @Test
    public void test36(Object src, MyValue2[] dst) {
        System.arraycopy(src, 0, dst, 0, dst.length);
    }

    @Run(test = "test36")
    public void test36_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test36(src, dst);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test36(src, dst);
            verify(src, dst);
        }
    }

    @Test
    public void test37(MyValue2[] src, Object dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Run(test = "test37")
    public void test37_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test37(src, dst);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test37(src, dst);
            verify(src, dst);
        }
    }

    @Test
    public void test38(Object src, MyValue2[] dst) {
        System.arraycopy(src, 0, dst, 0, dst.length);
    }

    @Run(test = "test38")
    @Warmup(1) // Avoid early compilation
    public void test38_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        Object[] src = new Object[len];
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test38(src, dst);
        verify(dst, src);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertDeoptimizedByC2(m);
            TestFramework.compile(m, CompLevel.C2);
            test38(src, dst);
            verify(dst, src);
            TestFramework.assertCompiledByC2(m);
        }
    }

    @Test
    public void test39(MyValue2[] src, Object dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Run(test = "test39")
    public void test39_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        Object[] dst = new Object[len];
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test39(src, dst);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test39(src, dst);
            verify(src, dst);
        }
    }

    @Test
    public void test40(Object[] src, Object dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Run(test = "test40")
    @Warmup(1) // Avoid early compilation
    public void test40_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        Object[] src = new Object[len];
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test40(src, dst);
        verify(dst, src);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertDeoptimizedByC2(m);
            TestFramework.compile(m, CompLevel.C2);
            test40(src, dst);
            verify(dst, src);
            TestFramework.assertCompiledByC2(m);
        }
    }

    @Test
    public void test41(Object src, Object[] dst) {
        System.arraycopy(src, 0, dst, 0, dst.length);
    }

    @Run(test = "test41")
    public void test41_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        Object[] dst = new Object[len];
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test41(src, dst);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test41(src, dst);
            verify(src, dst);
        }
    }

    @Test
    public void test42(Object[] src, Object[] dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Run(test = "test42")
    public void test42_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        Object[] src = new Object[len];
        Object[] dst = new Object[len];
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test42(src, dst);
        verify(src, dst);
        if (!info.isWarmUp()) {
            TestFramework.assertCompiledByC2(info.getTest());
        }
    }

    // short arraycopy()'s
    @Test
    public void test43(Object src, Object dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test43")
    public void test43_verifier(RunInfo info) {
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] dst = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        test43(src, dst);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test43(src, dst);
            verify(src, dst);
        }
    }

    @Test
    public void test44(Object src, MyValue2[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test44")
    public void test44_verifier(RunInfo info) {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test44(src, dst);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test44(src, dst);
            verify(src, dst);
        }
    }

    @Test
    public void test45(MyValue2[] src, Object dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test45")
    public void test45_verifier(RunInfo info) {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test45(src, dst);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test45(src, dst);
            verify(src, dst);
        }
    }

    @Test
    public void test46(Object[] src, MyValue2[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test46")
    @Warmup(1) // Avoid early compilation
    public void test46_verifier(RunInfo info) {
        Object[] src = new Object[8];
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test46(src, dst);
        verify(dst, src);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertDeoptimizedByC2(m);
            TestFramework.compile(m, CompLevel.C2);
            test46(src, dst);
            verify(dst, src);
            TestFramework.assertCompiledByC2(m);
        }
    }

    @Test
    public void test47(MyValue2[] src, Object[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test47")
    public void test47_verifier(RunInfo info) {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        Object[] dst = new Object[8];
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test47(src, dst);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test47(src, dst);
            verify(src, dst);
        }
    }

    @Test
    public void test48(Object[] src, Object dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test48")
    @Warmup(1) // Avoid early compilation
    public void test48_verifier(RunInfo info) {
        Object[] src = new Object[8];
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test48(src, dst);
        verify(dst, src);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertDeoptimizedByC2(m);
            TestFramework.compile(m, CompLevel.C2);
            test48(src, dst);
            verify(dst, src);
            TestFramework.assertCompiledByC2(m);
        }
    }

    @Test
    public void test49(Object src, Object[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test49")
    public void test49_verifier(RunInfo info) {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        Object[] dst = new Object[8];
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test49(src, dst);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test49(src, dst);
            verify(src, dst);
        }
    }

    @Test
    public void test50(Object[] src, Object[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test50")
    public void test50_verifier(RunInfo info) {
        Object[] src = new Object[8];
        Object[] dst = new Object[8];
        for (int i = 0; i < 8; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test50(src, dst);
        verify(src, dst);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiledByC2(m);
        }
    }

    @Test
    public MyValue1[] test51(MyValue1[] va) {
        Object[] res = Arrays.copyOf(va, va.length, MyValue1[].class);
        return (MyValue1[]) res;
    }

    @Run(test = "test51")
    public void test51_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        MyValue1[] result = test51(va);
        verify(va, result);
    }

    static final MyValue1[] test52_va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);

    @Test
    public MyValue1[] test52() {
        Object[] res = Arrays.copyOf(test52_va, 8, MyValue1[].class);
        return (MyValue1[]) res;
    }

    @Run(test = "test52")
    public void test52_verifier() {
        for (int i = 0; i < 8; ++i) {
            test52_va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        MyValue1[] result = test52();
        verify(test52_va, result);
    }

    @Test
    public MyValue1[] test53(Object[] va) {
        Object[] res = Arrays.copyOf(va, va.length, MyValue1[].class);
        return (MyValue1[]) res;
    }

    @Run(test = "test53")
    public void test53_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        MyValue1[] result = test53(va);
        verify(result, va);
    }

    @Test
    public Object[] test54(MyValue1[] va) {
        return Arrays.copyOf(va, va.length, Object[].class);
    }

    @Run(test = "test54")
    public void test54_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        Object[] result = test54(va);
        verify(va, result);
    }

    @Test
    public Object[] test55(Object[] va) {
        return Arrays.copyOf(va, va.length, Object[].class);
    }

    @Run(test = "test55")
    public void test55_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        Object[] result = test55(va);
        verify(va, result);
    }

    @Test
    public MyValue1[] test56(Object[] va) {
        Object[] res = Arrays.copyOf(va, va.length, MyValue1[].class);
        return (MyValue1[]) res;
    }

    @Run(test = "test56")
    public void test56_verifier() {
        int len = Math.abs(rI) % 10;
        Object[] va = new Object[len];
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        MyValue1[] result = test56(va);
        verify(result, va);
    }

    @Test
    public Object[] test57(Object[] va, Class klass) {
        return Arrays.copyOf(va, va.length, klass);
    }

    @Run(test = "test57")
    public void test57_verifier() {
        int len = Math.abs(rI) % 10;
        Object[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        Object[] result = test57(va, MyValue1[].class);
        verify(va, result);
    }

    @Test
    public Object[] test58(MyValue1[] va, Class klass) {
        return Arrays.copyOf(va, va.length, klass);
    }

    @Run(test = "test58")
    public void test58_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        for (int i = 0; i < 10; i++) {
            Object[] result = test58(va, MyValue1[].class);
            verify(va, result);
        }
        if (compile_and_run_again_if_deoptimized(info)) {
            Object[] result = test58(va, MyValue1[].class);
            verify(va, result);
        }
    }

    @Test
    public Object[] test59(MyValue1[] va) {
        return Arrays.copyOf(va, va.length+1, va.getClass());
    }

    @Run(test = "test59")
    public void test59_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullableAtomicArray(MyValue1.class, len);
        MyValue1[] verif = (MyValue1[])ValueClass.newNullableAtomicArray(MyValue1.class, len + 1);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
            verif[i] = va[i];
        }
        Object[] result = test59(va);
        verify(verif, result);
    }

    @Test
    public Object[] test60(Object[] va, Class klass) {
        return Arrays.copyOf(va, va.length+1, klass);
    }

    @Run(test = "test60")
    public void test60_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullableAtomicArray(MyValue1.class, len);
        MyValue1[] verif = (MyValue1[])ValueClass.newNullableAtomicArray(MyValue1.class, len + 1);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
            verif[i] = (MyValue1)va[i];
        }
        Object[] result = test60(va, va.getClass());
        verify(verif, result);
    }

    @Test
    public Object[] test61(Object[] va, Class klass) {
        return Arrays.copyOf(va, va.length+1, klass);
    }

    @Run(test = "test61")
    public void test61_verifier() {
        int len = Math.abs(rI) % 10;
        Object[] va = new NonValueClass[len];
        for (int i = 0; i < len; ++i) {
            va[i] = new NonValueClass(rI);
        }
        Object[] result = test61(va, NonValueClass[].class);
        for (int i = 0; i < va.length; ++i) {
            Asserts.assertEQ(va[i], result[i]);
        }
    }

    @ForceInline
    public Object[] test62_helper(int i, MyValue1[] va, NonValueClass[] oa) {
        Object[] arr = null;
        if (i == 10) {
            arr = oa;
        } else {
            arr = va;
        }
        return arr;
    }

    @Test
    public Object[] test62(MyValue1[] va, NonValueClass[] oa) {
        int i = 0;
        for (; i < 10; i++);

        Object[] arr = test62_helper(i, va, oa);

        return Arrays.copyOf(arr, arr.length+1, arr.getClass());
    }

    @Run(test = "test62")
    public void test62_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        NonValueClass[] oa = new NonValueClass[len];
        for (int i = 0; i < len; ++i) {
            oa[i] = new NonValueClass(rI);
        }
        test62_helper(42, va, oa);
        Object[] result = test62(va, oa);
        for (int i = 0; i < va.length; ++i) {
            Asserts.assertEQ(oa[i], result[i]);
        }
    }

    @ForceInline
    public Object[] test63_helper(int i, MyValue1[] va, NonValueClass[] oa) {
        Object[] arr = null;
        if (i == 10) {
            arr = va;
        } else {
            arr = oa;
        }
        return arr;
    }

    @Test
    public Object[] test63(MyValue1[] va, NonValueClass[] oa) {
        int i = 0;
        for (; i < 10; i++);

        Object[] arr = test63_helper(i, va, oa);

        return Arrays.copyOf(arr, arr.length+1, arr.getClass());
    }

    @Run(test = "test63")
    public void test63_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullableAtomicArray(MyValue1.class, len);
        MyValue1[] verif = (MyValue1[])ValueClass.newNullableAtomicArray(MyValue1.class, len + 1);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
            verif[i] = va[i];
        }
        NonValueClass[] oa = new NonValueClass[len];
        test63_helper(42, va, oa);
        Object[] result = test63(va, oa);
        verify(verif, result);
    }

    // Test initialization of value class arrays: small array
    @Test
    public MyValue1[] test64() {
        return (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
    }

    @Run(test = "test64")
    public void test64_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] var = test64();
        for (int i = 0; i < 8; ++i) {
            Asserts.assertEQ(va[i].hashPrimitive(), var[i].hashPrimitive());
        }
    }

    // Test initialization of value class arrays: large array
    @Test
    public MyValue1[] test65() {
        return (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 32, MyValue1.DEFAULT);
    }

    @Run(test = "test65")
    public void test65_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 32, MyValue1.DEFAULT);
        MyValue1[] var = test65();
        for (int i = 0; i < 32; ++i) {
            Asserts.assertEQ(va[i].hashPrimitive(), var[i].hashPrimitive());
        }
    }

    // Check init store elimination
    @Test
    @IR(counts = {ALLOC_ARRAY_OF_MYVALUE_KLASS, "= 1"})
    public MyValue1[] test66(MyValue1 vt) {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        va[0] = vt;
        return va;
    }

    @Run(test = "test66")
    public void test66_verifier() {
        MyValue1 vt = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue1[] va = test66(vt);
        Asserts.assertEQ(va[0].hashPrimitive(), vt.hashPrimitive());
    }

    // Zeroing elimination and arraycopy
    @Test
    public MyValue1[] test67(MyValue1[] src) {
        MyValue1[] dst = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 16, MyValue1.DEFAULT);
        System.arraycopy(src, 0, dst, 0, 13);
        return dst;
    }

    @Run(test = "test67")
    public void test67_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 16, MyValue1.DEFAULT);
        MyValue1[] var = test67(va);
        for (int i = 0; i < 16; ++i) {
            Asserts.assertEQ(va[i].hashPrimitive(), var[i].hashPrimitive());
        }
    }

    // A store with an all-zero value can be eliminated
    @Test
    public MyValue1[] test68() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        va[0] = va[1];
        return va;
    }

    @Run(test = "test68")
    public void test68_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        MyValue1[] var = test68();
        for (int i = 0; i < 2; ++i) {
            Asserts.assertEQ(va[i].hashPrimitive(), var[i].hashPrimitive());
        }
    }

    // Requires individual stores to init array
    @Test
    public MyValue1[] test69(MyValue1 vt) {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 4, MyValue1.DEFAULT);
        va[0] = vt;
        va[3] = vt;
        return va;
    }

    @Run(test = "test69")
    public void test69_verifier() {
        MyValue1 vt = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 4, MyValue1.DEFAULT);
        va[0] = vt;
        va[3] = vt;
        MyValue1[] var = test69(vt);
        for (int i = 0; i < va.length; ++i) {
            Asserts.assertEQ(va[i].hashPrimitive(), var[i].hashPrimitive());
        }
    }

    // Same as test68 but store is further away from allocation
    @Test
    public MyValue1[] test70(MyValue1[] other) {
        other[1] = other[0];
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        other[0] = va[1];
        va[0] = va[1];
        return va;
    }

    @Run(test = "test70")
    public void test70_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        MyValue1[] var = test70(va);
        for (int i = 0; i < 2; ++i) {
            Asserts.assertEQ(va[i].hashPrimitive(), var[i].hashPrimitive());
        }
    }

    // EA needs to consider oop fields in flattened arrays
    @Test
    public void test71() {
        int len = 10;
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsDontInline(rI+i, rD+i);
        }
        System.arraycopy(src, 0, dst, 0, src.length);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(src[i].hash(), dst[i].hash());
        }
    }

    @Run(test = "test71")
    public void test71_verifier() {
        test71();
    }

    // Test EA with leaf call to 'store_unknown_inline'
    @Test
    public void test72(Object[] o, boolean b, Object element) {
        Object[] arr1 = new Object[10];
        Object[] arr2 = new Object[10];
        if (b) {
            arr1 = o;
        }
        arr1[0] = element;
        arr2[0] = element;
    }

    @Run(test = "test72")
    public void test72_verifier() {
        Object[] arr = new Object[1];
        Object elem = new Object();
        test72(arr, true, elem);
        test72(arr, false, elem);
    }

    @Test
    public void test73(Object[] oa, MyValue1 v, Object o) {
        // TestLWorld.test38 use a C1 Phi node for the array. This test
        // adds the case where the stored value is a C1 Phi node.
        Object o2 = (o == null) ? v : o;
        oa[0] = v;  // The stored value is known to be flattenable
        oa[1] = o;  // The stored value may be flattenable
        oa[2] = o2; // The stored value may be flattenable (a C1 Phi node)
        oa[0] = oa; // The stored value is known to be not flattenable (an Object[])
    }

    @Run(test = "test73")
    public void test73_verifier() {
        MyValue1 v0 = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue1 v1 = MyValue1.createWithFieldsDontInline(rI+1, rL+1);
        MyValue1[] arr = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 3, MyValue1.DEFAULT);
        try {
            test73(arr, v0, v1);
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException e) {
            // expected
        }
        Asserts.assertEQ(v0, arr[0]);
        Asserts.assertEQ(v1, arr[1]);
        Asserts.assertEQ(v1, arr[2]);
    }

    public static void test74Callee(MyValue1[] va) { }

    // Tests invoking unloaded method with value class array in signature
    @Test
    public void test74(MethodHandle m, MyValue1[] va) throws Throwable {
        m.invoke(va);
    }

    @Run(test = "test74")
    @Warmup(0)
    public void test74_verifier() throws Throwable {
        MethodHandle m = MethodHandles.lookup().findStatic(TestArrays.class, "test74Callee", MethodType.methodType(void.class, MyValue1[].class));
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 0, MyValue1.DEFAULT);
        test74(m, va);
    }

    // Some more array clone tests
    @ForceInline
    public Object[] test75_helper(int i, MyValue1[] va, NonValueClass[] oa) {
        Object[] arr = null;
        if (i == 10) {
            arr = oa;
        } else {
            arr = va;
        }
        return arr;
    }

    @Test
    public Object[] test75(MyValue1[] va, NonValueClass[] oa) {
        int i = 0;
        for (; i < 10; i++);

        Object[] arr = test75_helper(i, va, oa);
        return arr.clone();
    }

    @Run(test = "test75")
    public void test75_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        NonValueClass[] oa = new NonValueClass[len];
        for (int i = 0; i < len; ++i) {
            oa[i] = new NonValueClass(rI);
        }
        test75_helper(42, va, oa);
        Object[] result = test75(va, oa);

        for (int i = 0; i < va.length; ++i) {
            Asserts.assertEQ(oa[i], result[i]);
            // Check that array has correct properties (null-ok)
            result[i] = null;
        }
    }

    @ForceInline
    public Object[] test76_helper(int i, MyValue1[] va, NonValueClass[] oa) {
        Object[] arr = null;
        if (i == 10) {
            arr = va;
        } else {
            arr = oa;
        }
        return arr;
    }

    @Test
    public Object[] test76(MyValue1[] va, NonValueClass[] oa) {
        int i = 0;
        for (; i < 10; i++);

        Object[] arr = test76_helper(i, va, oa);
        return arr.clone();
    }

    @Run(test = "test76")
    public void test76_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] verif = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
            verif[i] = va[i];
        }
        NonValueClass[] oa = new NonValueClass[len];
        test76_helper(42, va, oa);
        Object[] result = test76(va, oa);
        verify(verif, result);
        // Check that array has correct properties (null-free)
        if (len > 0) {
            try {
                result[0] = null;
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    @Test
    public void test77() {
        MyValue1 v0 = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue1 v1 = MyValue1.createWithFieldsDontInline(rI+1, rL+1);
        MyValue1[] arr = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);

        Object[] oa = arr;
        Object o1 = v1;
        Object o = (o1 == null) ? v0 : o1;

        oa[0] = o; // For C1, due to IfOp optimization, the declared_type of o becomes NULL.

        Asserts.assertEQ(arr[0].hash(), v1.hash());
    }


    @Run(test = "test77")
    public void test77_verifier() {
        test77();
    }

    @Test
    public long test78(MyValue1 v, int n) {
        long x = 0;
        for (int i = 0; i < n; i++) {
        }

        MyValue1[] a = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, n, MyValue1.DEFAULT);
        a[0] = v;
        for (int i = 0; i<n; i++) {
            x += a[i].hash(); // C1 PhiSimplifier changes "a" from a Phi node to a NewObjectArray node
        }

        return x;
    }

    @Run(test = "test78")
    public void test78_verifier() {
        MyValue1 v = MyValue1.createWithFieldsInline(rI, rL);
        Asserts.assertEQ(test78(v, 1), v.hash());
    }

    // Verify that casting an array element to a non-flattenable type marks the array as not flat
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {LOAD_UNKNOWN_INLINE, "= 1"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = {ALLOC, ALLOC_ARRAY, LOAD_UNKNOWN_INLINE})
    public Object test79(Object[] array, int i) {
        NonValueClass i1 = (NonValueClass)array[0];
        Object o = array[1];
        return array[i];
    }

    @Run(test = "test79")
    public void test79_verifier() {
        NonValueClass obj = new NonValueClass(rI);
        NonValueClass[] array = new NonValueClass[2];
        array[1] = obj;
        Object result = test79(array, 1);
        Asserts.assertEquals(result, obj);
    }

    // Same as test79 but with not flattenable value class
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {LOAD_UNKNOWN_INLINE, "= 1"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = {ALLOC, ALLOC_ARRAY, LOAD_UNKNOWN_INLINE})
    public Object test80(Object[] array, int i) {
        NotFlattenable vt = (NotFlattenable)array[0];
        Object o = array[1];
        return array[i];
    }

    @Run(test = "test80")
    public void test80_verifier() {
        NotFlattenable vt = new NotFlattenable();
        NotFlattenable[] array = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 2, NotFlattenable.DEFAULT);
        array[1] = vt;
        Object result = test80(array, 1);
        Asserts.assertEquals(result, vt);
    }

    // Verify that writing an object of a non-inline, non-null type to an array marks the array as not null-free and not flat
    @Test
    @IR(failOn = {ALLOC, ALLOC_ARRAY, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE, INLINE_ARRAY_NULL_GUARD})
    public Object test81(Object[] array, NonValueClass v, Object o, int i) {
        if (v == null) {
          return null;
        }
        array[0] = v;
        array[1] = array[0];
        array[2] = o;
        return array[i];
    }

    @Run(test = "test81")
    public void test81_verifier() {
        NonValueClass obj = new NonValueClass(rI);
        NonValueClass[] array1 = new NonValueClass[3];
        Object[] array2 = new Object[3];
        Object result = test81(array1, obj, obj, 0);
        Asserts.assertEquals(array1[0], obj);
        Asserts.assertEquals(array1[1], obj);
        Asserts.assertEquals(array1[2], obj);
        Asserts.assertEquals(result, obj);
        result = test81(array2, obj, obj, 1);
        Asserts.assertEquals(array2[0], obj);
        Asserts.assertEquals(array2[1], obj);
        Asserts.assertEquals(array2[2], obj);
        Asserts.assertEquals(result, obj);
    }

    // Verify that writing an object of a non-flattenable value class to an array marks the array as not flat
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        failOn = {ALLOC_ARRAY, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE})
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC, ALLOC_ARRAY, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE})
    public Object test82(Object[] array, NotFlattenable vt, Object o, int i) {
        array[0] = vt;
        array[1] = array[0];
        array[2] = o;
        return array[i];
    }

    @Run(test = "test82")
    public void test82_verifier() {
        NotFlattenable vt = new NotFlattenable();
        NotFlattenable[] array1 = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 3, NotFlattenable.DEFAULT);
        Object[] array2 = new Object[3];
        Object result = test82(array1, vt, vt, 0);
        Asserts.assertEquals(array1[0], vt);
        Asserts.assertEquals(array1[1], vt);
        Asserts.assertEquals(array1[2], vt);
        Asserts.assertEquals(result, vt);
        result = test82(array2, vt, vt, 1);
        Asserts.assertEquals(array2[0], vt);
        Asserts.assertEquals(array2[1], vt);
        Asserts.assertEquals(array2[2], vt);
        Asserts.assertEquals(result, vt);
    }

    // Verify that casting an array element to a non-value class type type marks the array as not null-free and not flat
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {LOAD_UNKNOWN_INLINE, "= 1"},
        failOn = {ALLOC_ARRAY, STORE_UNKNOWN_INLINE, INLINE_ARRAY_NULL_GUARD})
    @IR(applyIf = {"UseArrayFlattening", "false"},
            failOn = {ALLOC, ALLOC_ARRAY, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE, INLINE_ARRAY_NULL_GUARD})
    public void test83(Object[] array, Object o) {
        NonValueClass i = (NonValueClass)array[0];
        array[1] = o;
    }

    @Run(test = "test83")
    public void test83_verifier() {
        NonValueClass obj = new NonValueClass(rI);
        NonValueClass[] array1 = new NonValueClass[2];
        Object[] array2 = new Object[2];
        test83(array1, obj);
        Asserts.assertEquals(array1[1], obj);
        test83(array2, null);
        Asserts.assertEquals(array2[1], null);
    }

    // Verify that writing constant null into an array marks the array as not null-free
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        failOn = {ALLOC, ALLOC_ARRAY},
        counts = {INLINE_ARRAY_NULL_GUARD, "= 1", // Null check on first store, no check on second store
                  STORE_UNKNOWN_INLINE, "= 2",
                  LOAD_UNKNOWN_INLINE, "= 1"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = {ALLOC, ALLOC_ARRAY, STORE_UNKNOWN_INLINE, LOAD_UNKNOWN_INLINE},
        counts = {INLINE_ARRAY_NULL_GUARD, "= 1"}) // Null check on first store, no check on second store
    public Object test84(Object[] array, int i) {
        array[0] = null;
        array[1] = null;
        return array[i];
    }

    @Run(test = "test84")
    public void test84_verifier(RunInfo info) {
        NotFlattenable[] array1 = new NotFlattenable[2];
        Object[] array2 = new Object[2];
        Object result = test84(array1, 0);
        Asserts.assertEquals(array1[0], null);
        Asserts.assertEquals(result, null);
        result = test84(array2, 1);
        Asserts.assertEquals(array2[0], null);
        Asserts.assertEquals(result, null);
        if (!info.isWarmUp()) {
            NotFlattenable[] array3 = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 2, NotFlattenable.DEFAULT);
            try {
                test84(array3, 1);
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Similar to test84 but with branches
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        failOn = {ALLOC, ALLOC_ARRAY},
        counts = {INLINE_ARRAY_NULL_GUARD, "= 2",
                  STORE_UNKNOWN_INLINE, "<= 3"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = {ALLOC, ALLOC_ARRAY, STORE_UNKNOWN_INLINE, LOAD_UNKNOWN_INLINE},
        counts = {INLINE_ARRAY_NULL_GUARD, "= 2"})
    public void test85(Object[] array, Object o, boolean b) {
        if (b) {
            array[0] = null;
        } else {
            array[1] = null;
        }
        array[1] = o;
    }

    @Run(test = "test85")
    public void test85_verifier(RunInfo info) {
        NonValueClass obj = new NonValueClass(rI);
        NonValueClass[] array1 = new NonValueClass[2];
        Object[] array2 = new Object[2];
        test85(array1, obj, true);
        Asserts.assertEquals(array1[1], obj);
        test85(array1, null, false);
        Asserts.assertEquals(array1[1], null);
        test85(array2, obj, true);
        Asserts.assertEquals(array2[1], obj);
        test85(array2, null, false);
        Asserts.assertEquals(array2[1], null);
        if (!info.isWarmUp()) {
            NotFlattenable[] array3 = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 2, NotFlattenable.DEFAULT);
            try {
                test85(array3, null, true);
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Same as test85 but with not flattenable value class array
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        failOn = {ALLOC_ARRAY, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE},
        counts = {INLINE_ARRAY_NULL_GUARD, "= 2", ALLOC, "= 1"})
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC, ALLOC_ARRAY, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE},
        counts = {INLINE_ARRAY_NULL_GUARD, "= 2"})
    public void test86(NotFlattenable[] array, NotFlattenable o, boolean b) {
        if (b) {
            array[0] = null;
        } else {
            array[1] = null;
        }
        array[1] = o;
    }

    @Run(test = "test86")
    public void test86_verifier(RunInfo info) {
        NotFlattenable vt = new NotFlattenable();
        NotFlattenable[] array1 = new NotFlattenable[2];
        test86(array1, vt, true);
        Asserts.assertEquals(array1[1], vt);
        test86(array1, null, false);
        Asserts.assertEquals(array1[1], null);
        if (!info.isWarmUp()) {
            NotFlattenable[] array2 = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 2, NotFlattenable.DEFAULT);
            try {
                test86(array2, null, true);
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Same as test85 but with value class array
    @Test
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "true"},
        failOn = {ALLOC_ARRAY, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE},
        counts = {INLINE_ARRAY_NULL_GUARD, "= 2", ALLOC, "= 1"})
    @IR(applyIf = {"InlineTypePassFieldsAsArgs", "false"},
        failOn = {ALLOC, ALLOC_ARRAY, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE},
        counts = {INLINE_ARRAY_NULL_GUARD, "= 2"})
    public void test87(MyValue1[] array, MyValue1 o, boolean b) {
        if (b) {
            array[0] = null;
        } else {
            array[1] = null;
        }
        array[1] = o;
    }

    @Run(test = "test87")
    public void test87_verifier(RunInfo info) {
        MyValue1 vt = MyValue1.createWithFieldsInline(rI, rL);
        MyValue1[] array1 = new MyValue1[2];
        test87(array1, vt, true);
        Asserts.assertEquals(array1[1], vt);
        test87(array1, null, false);
        Asserts.assertEquals(array1[1], null);
        if (!info.isWarmUp()) {
            MyValue1[] array2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
            try {
                test87(array2, null, true);
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Additional correctness tests to make sure we have the required null checks
    @Test
    public void test88(Object[] array, NonValueClass v) {
        array[0] = v;
    }

    @Run(test = "test88")
    public void test88_verifier(RunInfo info) {
        NonValueClass[] array1 = new NonValueClass[1];
        Object[] array2 = new Object[1];
        test88(array1, null);
        Asserts.assertEquals(array1[0], null);
        test88(array2, null);
        Asserts.assertEquals(array2[0], null);
        if (!info.isWarmUp()) {
            MyValue1[] array3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
            try {
                test88(array3, null);
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    @Test
    public void test89(MyValue1[] array, NonValueClass v) {
        Object o = v;
        array[0] = (MyValue1)o;
    }

    @Run(test = "test89")
    public void test89_verifier(RunInfo info) {
        MyValue1[] array1 = new MyValue1[1];
        test89(array1, null);
        Asserts.assertEquals(array1[0], null);
        if (!info.isWarmUp()) {
            MyValue1[] array2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
            try {
                test89(array2, null);
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    @Test
    public boolean test90() {
        boolean b = true;

        MyValue1[] qArray = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 0, MyValue1.DEFAULT);
        MyValue1[] lArray = new MyValue1[0];

        b = b && (qArray instanceof MyValue1[]);
        b = b && (lArray instanceof MyValue1[]);

        MyValue1[][] qArray2 = { (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 0, MyValue1.DEFAULT) };
        MyValue1[][] lArray2 = new MyValue1[0][0];

        b = b && (qArray2 instanceof MyValue1[][]);
        b = b && (lArray2 instanceof MyValue1[][]);

        return b;
    }

    @Run(test = "test90")
    public void test90_verifier() {
        Asserts.assertEQ(test90(), true);
    }

    @LooselyConsistentValue
    static value class Test91Value {
        public int f0;
        public int f1;
        public int f2;
        public int f3;
        public int f4;
        public int f5;

        public Test91Value(int i) {
            this.f0 = i;
            this.f1 = i;
            this.f2 = i;
            this.f3 = i;
            this.f4 = i;
            this.f5 = i;
        }

        public void verify() {
            if ((f0 != f1) || (f1 != f2) || (f2 != f3) || (f3 != f4) || (f4 != f5)) {
                throw new RuntimeException("test91 failed");
            }
        }
    }

    // Test anti-dependencies between loads and stores from flattened array
    @Test
    public int test91(Test91Value[] array, int lo, int val) {
        int i = 3;
        while (lo < i) {
            Test91Value tmp = array[lo];
            array[lo++] = array[i];
            array[i--] = tmp;
        }
        return val;
    }

    @Run(test = "test91")
    @Warmup(0)
    public void test91_verifier() {
        Test91Value[] array = (Test91Value[])ValueClass.newNullRestrictedNonAtomicArray(Test91Value.class, 5, new Test91Value(0));
        for (int i = 0; i < 5; ++i) {
            array[i] = new Test91Value(i);
            array[i].verify();
        }
        Asserts.assertEQ(test91(array, 0, 5), 5);
        for (int i = 0; i < 5; ++i) {
            array[i].verify();
        }
    }

    @Test
    public void test92(Object[] src, Object[] dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Run(test = "test92")
    public void test92_verifier() {
        MyValue1[] a = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        MyValue1[] b = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        try {
            test92(a, null);
            throw new RuntimeException("Should throw NullPointerException");
        } catch (NullPointerException expected) {}

        try {
            test92(null, b);
            throw new RuntimeException("Should throw NullPointerException");
        } catch (NullPointerException expected) {}

        a[0] = MyValue1.createWithFieldsInline(rI, rL);
        test92(a, b);
        verify(a, b);
    }

    // Same as test30 but accessing all elements of the non-escaping array
    @Test
    public long test93(MyValue2[] src, boolean flag) {
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        System.arraycopy(src, 0, dst, 0, 10);
        if (flag) {  }
        return dst[0].hash() + dst[1].hash() + dst[2].hash() + dst[3].hash() + dst[4].hash() +
               dst[5].hash() + dst[6].hash() + dst[7].hash() + dst[8].hash() + dst[9].hash();
    }

    @Run(test = "test93")
    @Warmup(10000)
    public void test93_verifier(RunInfo info) {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        for (int i = 0; i < 10; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        long res = test93(src, !info.isWarmUp());
        long expected = 0;
        for (int i = 0; i < 10; ++i) {
            expected += src[i].hash();
        }
        Asserts.assertEQ(res, expected);
    }

    // Same as test93 but with variable source array offset
    @Test
    public long test94(MyValue2[] src, int i, boolean flag) {
        MyValue2[] dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        System.arraycopy(src, i, dst, 0, 1);
        if (flag) {  }
        return dst[0].hash() + dst[1].hash() + dst[2].hash() + dst[3].hash() + dst[4].hash() +
               dst[5].hash() + dst[6].hash() + dst[7].hash() + dst[8].hash() + dst[9].hash();
    }

    @Run(test = "test94")
    @Warmup(10000)
    public void test94_verifier(RunInfo info) {
        MyValue2[] src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        for (int i = 0; i < 10; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        for (int i = 0; i < 10; ++i) {
            long res = test94(src, i, !info.isWarmUp());
            long expected = src[i].hash() + 9*MyValue2.createDefaultInline().hash();
            Asserts.assertEQ(res, expected);
        }
    }

    static final MyValue1[] nullFreeArray = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);

    // Test propagation of not null-free/flat information
    @Test
    public MyValue1[] test95(Object[] array) {
        array[0] = null;
        return nullFreeArray.getClass().cast(array);
    }

    @Run(test = "test95")
    public void test95_verifier() {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        NonValueClass[] array2 = new NonValueClass[1];
        try {
            test95(array1);
            throw new RuntimeException("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            test95(array2);
            throw new RuntimeException("Should throw ClassCastException");
        } catch (ClassCastException e) {
            // Expected
        }
    }

    // Same as test95 but with cmp user of cast result
    @Test
    public boolean test96(Object[] array) {
        array[0] = null;
        MyValue1[] casted = nullFreeArray.getClass().cast(array);
        return casted != null;
    }

    @Run(test = "test96")
    public void test96_verifier() {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        NonValueClass[] array2 = new NonValueClass[1];
        try {
            test96(array1);
            throw new RuntimeException("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            test96(array2);
            throw new RuntimeException("Should throw ClassCastException");
        } catch (ClassCastException e) {
            // Expected
        }
    }

    // Same as test95 but with instanceof instead of cast
    @Test
    @IR(applyIf = {"MonomorphicArrayCheck", "true"}, failOn = IRNode.SUBTYPE_CHECK)
    public boolean test97(Object[] array) {
        // When calling this with array = MyValue1[], we will already throw an ArrayStoreException here.
        array[0] = new NonValueClass(42);
        // Always returns false because we just successfully stored a non-value object and therefore the array can't
        // be a value class array.
        return array instanceof MyValue1[];
    }

    @Run(test = "test97")
    public void test97_verifier(RunInfo runInfo) {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        NonValueClass[] array2 = new NonValueClass[1];
        // When emitting the array store check "NonValueClass <: Object[]" for "array[0] = new NonValueClass(42)" in
        // test97(), we speculatively assume that Object[] is exact and emit such a check with an uncommon trap before
        // the array store check at the same bci. We propagate that information with an additional CheckCastPP node
        // feeding into the array store sub type check.
        // At runtime, we will hit the ArrayStoreException in the first execution when array is a MyValue1[].
        // With the default IR framework warm-up, we will profile the ArrayStoreException in the interpreter and
        // pass it in the MDO to the C2 compiler which treats these exceptions as traps being hit (see
        // InterpreterRuntime::create_klass_exception). As a result, C2 is not able to speculatively cast the array of
        // type Object[] to an exact type before the first sub type check because we've seen too many traps being taken
        // at that bci due to the ArrayStoreException that was hit at the very same bci (see Compile::too_many_traps()
        // which checks that zero traps have been taken so far). Thus, neither the first sub type check for the array
        // check cast nor the second sub type check for the instanceof can be removed.
        // By not executing test97() with MyValue1[] during warm-up, which would trigger the ArrayStoreException,
        // we will not observe an ArrayStoreException before C2 compilation. Note that C2 also requires
        // MonomorphicArrayCheck in order to emit the speculative exactness check.
        // The same is required for test98-100().
        if (!runInfo.isWarmUp()) {
            try {
                test97(array1);
                throw new RuntimeException("Should throw ArrayStoreException");
            } catch (ArrayStoreException e) {
                // Expected
            }
        }
        boolean res = test97(array2);
        Asserts.assertFalse(res);
    }

    // Same as test95 but with non-flattenable store
    @Test
    @IR(applyIfAnd = {"UseArrayFlattening", "true", "MonomorphicArrayCheck", "true"},
        failOn = IRNode.SUBTYPE_CHECK)
    public MyValue1[] test98(Object[] array) {
        // When calling this with array = MyValue1[], we will already throw an ArrayStoreException here.
        array[0] = new NotFlattenable();
        // Always throws a ClassCastException because we just successfully stored a
        // non-flattenable value and therefore the array can't be a flat array.
        return (MyValue1[])array;
    }

    @Run(test = "test98")
    public void test98_verifier(RunInfo runInfo) {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        NotFlattenable[] array2 = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 1, NotFlattenable.DEFAULT);
        if (!runInfo.isWarmUp()) { // See test97() for the reason why we need this.
            try {
                test98(array1);
                throw new RuntimeException("Should throw ArrayStoreException");
            } catch (ArrayStoreException e) {
                // Expected
            }
        }
        try {
            test98(array2);
            throw new RuntimeException("Should throw ClassCastException");
        } catch (ClassCastException e) {
            // Expected
        }
    }

    // Same as test98 but with cmp user of cast result
    @Test
    @IR(applyIfAnd = {"UseArrayFlattening", "true", "MonomorphicArrayCheck", "true"},
        failOn = IRNode.SUBTYPE_CHECK)
    public boolean test99(Object[] array) {
        // When calling this with array = MyValue1[], we will already throw an ArrayStoreException here.
        array[0] = new NotFlattenable();
        // Always throws a ClassCastException because we just successfully stored a
        // non-flattenable value and therefore the array can't be a flat array.
        MyValue1[] casted = (MyValue1[])array;
        return casted != null;
    }

    @Run(test = "test99")
    public void test99_verifier(RunInfo runInfo) {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        NotFlattenable[] array2 = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 1, NotFlattenable.DEFAULT);
        if (!runInfo.isWarmUp()) { // See test97() for the reason why we need this.
            try {
                test99(array1);
                throw new RuntimeException("Should throw ArrayStoreException");
            } catch (ArrayStoreException e) {
                // Expected
            }
        }
        try {
            test99(array2);
            throw new RuntimeException("Should throw ClassCastException");
        } catch (ClassCastException e) {
            // Expected
        }
    }

    // Same as test98 but with instanceof instead of cast
    @Test
    @IR(applyIfAnd = {"UseArrayFlattening", "true", "MonomorphicArrayCheck", "true"},
        failOn = IRNode.SUBTYPE_CHECK)
    public boolean test100(Object[] array) {
        // When calling this with array = MyValue1[], we will already throw an ArrayStoreException here.
        array[0] = new NotFlattenable();
        // Always throws a ClassCastException because we just successfully stored a
        // non-flattenable value and therefore the array can't be a flat array.
        return array instanceof MyValue1[];
    }

    @Run(test = "test100")
    public void test100_verifier(RunInfo runInfo) {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        NotFlattenable[] array2 = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 1, NotFlattenable.DEFAULT);
        if (!runInfo.isWarmUp()) { // See test97() for the reason why we need this.
            try {
                test100(array1);
                throw new RuntimeException("Should throw ArrayStoreException");
            } catch (ArrayStoreException e) {
                // Expected
            }
        }
        boolean res = test100(array2);
        Asserts.assertFalse(res);
    }

    // Test that SUBTYPE_CHECK matching works as expected
    @Test
    @IR(counts = { IRNode.SUBTYPE_CHECK, "1" })
    public boolean test101(Object[] array) {
        return array instanceof MyValue1[];
    }

    @Run(test = "test101")
    public void test101_verifier() {
        MyValue1[] array1 = new MyValue1[1];
        NotFlattenable[] array2 = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 1, NotFlattenable.DEFAULT);
        MyValue1[] array3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        Asserts.assertTrue(test101(array1));
        Asserts.assertFalse(test101(array2));
        Asserts.assertTrue(test101(array3));
    }

    // Test that SUBTYPE_CHECK matching works as expected with null-free arrays
    @Test
    @IR(counts = { IRNode.SUBTYPE_CHECK, "1" })
    public Object test101NullFree(Object[] array) {
        return nullFreeArray.getClass().cast(array);
    }

    @Run(test = "test101NullFree")
    public void test101NullFree_verifier() {
        MyValue1[] array1 = new MyValue1[1];
        NotFlattenable[] array2 = (NotFlattenable[])ValueClass.newNullRestrictedNonAtomicArray(NotFlattenable.class, 1, NotFlattenable.DEFAULT);
        MyValue1[] array3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        test101NullFree(array1);
        try {
            test101NullFree(array2);
            throw new RuntimeException("Should throw ClassCastException");
        } catch (ClassCastException e) {
            // Expected
        }
        test101NullFree(array3);
    }

    // TODO 8251971 Used for copyOf because we can't create a null-free, non-atomic, flat array via the mirror as a destination array
    static final MyValue2[] val_src2 = new MyValue2[8];

    static final MyValue2[] val_src = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
    static final MyValue2[] val_dst = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
    static final Object[]   obj_src = new Object[8];
    static final Object[]   obj_null_src = new Object[8];
    static final Object[]   obj_dst = new Object[8];

    @ForceInline
    static Object get_val_src() { return val_src; }
    @ForceInline
    static Object get_val_dst() { return val_dst; }
    @ForceInline
    static Class get_val_class() { return val_src.getClass(); }
    @ForceInline
    static Class get_non_val_class() { return NonValueClass[].class; }
    @ForceInline
    static Object get_obj_src() { return obj_src; }
    @ForceInline
    static Object get_obj_null_src() { return obj_null_src; }
    @ForceInline
    static Object get_obj_dst() { return obj_dst; }
    @ForceInline
    static Class get_obj_class() { return Object[].class; }

    static {
        for (int i = 0; i < 8; ++i) {
            val_src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            obj_src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            obj_null_src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        obj_null_src[0] = null;
    }

    // Arraycopy with constant source and destination arrays
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, "= 1"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP)
    public void test102() {
        System.arraycopy(val_src, 0, obj_dst, 0, 8);
    }

    @Run(test = "test102")
    public void test102_verifier() {
        test102();
        verify(val_src, obj_dst);
    }

    // Same as test102 but with MyValue2[] dst
    @Test
    // TODO 8350865 Re-enable
    // @IR(failOn = INTRINSIC_SLOW_PATH)
    public void test103() {
        System.arraycopy(val_src, 0, val_dst, 0, 8);
    }

    @Run(test = "test103")
    public void test103_verifier() {
        test103();
        verify(val_src, val_dst);
    }

    // Same as test102 but with Object[] src
    @Test
    @IR(failOn = INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP)
    public void test104() {
        System.arraycopy(obj_src, 0, obj_dst, 0, 8);
    }

    @Run(test = "test104")
    public void test104_verifier() {
        test104();
        verify(obj_src, obj_dst);
    }

    // Same as test103 but with Object[] src
    @Test
    @IR(counts = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, "= 1"})
    public void test105() {
        System.arraycopy(obj_src, 0, val_dst, 0, 8);
    }

    @Run(test = "test105")
    public void test105_verifier() {
        test105();
        verify(obj_src, val_dst);
    }

    // Same as test103 but with Object[] src containing null
    @Test
    @IR(counts = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, "= 1"})
    public void test105_null() {
        System.arraycopy(obj_null_src, 0, val_dst, 0, 8);
    }

    @Run(test = "test105_null")
    public void test105_null_verifier() {
        try {
            test105_null();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // expected
        }
    }

    // Below tests are equal to test102-test105 but hide the src/dst types until
    // after the arraycopy intrinsic is emitted (with incremental inlining).
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, "= 1"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP)
    public void test106() {
        System.arraycopy(get_val_src(), 0, get_obj_dst(), 0, 8);
    }

    @Run(test = "test106")
    public void test106_verifier() {
        test106();
        verify(val_src, obj_dst);
    }

    // TODO 8251971: Should be optimized but we are bailing out because
    // at parse time it looks as if src could be flat and dst could be not flat.
    @Test
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP)
    public void test107() {
        System.arraycopy(get_val_src(), 0, get_val_dst(), 0, 8);
    }

    @Run(test = "test107")
    public void test107_verifier() {
        test107();
        verify(val_src, val_dst);
    }

    @Test
    @IR(failOn = INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP)
    public void test108() {
        System.arraycopy(get_obj_src(), 0, get_obj_dst(), 0, 8);
    }

    @Run(test = "test108")
    public void test108_verifier() {
        test108();
        verify(obj_src, obj_dst);
    }

    @Test
    @IR(counts = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, "= 1"})
    public void test109() {
        System.arraycopy(get_obj_src(), 0, get_val_dst(), 0, 8);
    }

    @Run(test = "test109")
    public void test109_verifier() {
        test109();
        verify(obj_src, val_dst);
    }

    @Test
    @IR(counts = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, "= 1"})
    public void test109_null() {
        System.arraycopy(get_obj_null_src(), 0, get_val_dst(), 0, 8);
    }

    @Run(test = "test109_null")
    public void test109_null_verifier() {
        try {
            test109_null();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // expected
        }
    }

    // Arrays.copyOf with constant source and destination arrays
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, "= 1"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, CLASS_CHECK_TRAP})
    public Object[] test110() {
        return Arrays.copyOf(val_src, 8, Object[].class);
    }

    @Run(test = "test110")
    public void test110_verifier() {
        Object[] res = test110();
        verify(val_src, res);
    }

    // Same as test110 but with MyValue2[] dst
    @Test
    @IR(failOn = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, CLASS_CHECK_TRAP})
    public Object[] test111() {
        return Arrays.copyOf(val_src2, 8, val_src2.getClass());
    }

    @Run(test = "test111")
    public void test111_verifier() {
        Object[] res = test111();
        verify(val_src2, res);
    }

    // Same as test110 but with Object[] src
    @Test
    @IR(failOn = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, CLASS_CHECK_TRAP})
    public Object[] test112() {
        return Arrays.copyOf(obj_src, 8, Object[].class);
    }

    @Run(test = "test112")
    public void test112_verifier() {
        Object[] res = test112();
        verify(obj_src, res);
    }

    // Same as test111 but with Object[] src
    @Test
    @IR(counts = {CLASS_CHECK_TRAP, " = 1"})
    public Object[] test113() {
        return Arrays.copyOf(obj_src, 8, MyValue2[].class);
    }

    @Run(test = "test113")
    public void test113_verifier() {
        Object[] res = test113();
        verify(obj_src, res);
    }

    // Same as test111 but with Object[] src containing null
    @Test
    @IR(counts = {CLASS_CHECK_TRAP, " = 1"})
    public Object[] test113_null() {
        return Arrays.copyOf(obj_null_src, 8, val_src.getClass());
    }

    @Run(test = "test113_null")
    public void test113_null_verifier() {
        Object[] res = test113_null();
        verify(obj_null_src, res);
    }

    // Below tests are equal to test110-test113 but hide the src/dst types until
    // after the arraycopy intrinsic is emitted (with incremental inlining).

    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, "= 1"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, CLASS_CHECK_TRAP})
    public Object[] test114() {
        return Arrays.copyOf((Object[])get_val_src(), 8, get_obj_class());
    }

    @Run(test = "test114")
    public void test114_verifier() {
        Object[] res = test114();
        verify(val_src, res);
    }

    // TODO 8251971: Should be optimized but we are bailing out because
    // at parse time it looks as if src could be flat and dst could be not flat
    @Test
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, CLASS_CHECK_TRAP})
    public Object[] test115() {
        return Arrays.copyOf((Object[])get_val_src(), 8, get_val_class());
    }

    @Run(test = "test115")
    public void test115_verifier() {
        Object[] res = test115();
        verify(val_src, res);
    }

    @Test
    @IR(failOn = {INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, CLASS_CHECK_TRAP})
    public Object[] test116() {
        return Arrays.copyOf((Object[])get_obj_src(), 8, get_obj_class());
    }

    @Run(test = "test116")
    public void test116_verifier() {
        Object[] res = test116();
        verify(obj_src, res);
    }

    @Test
    @IR(counts = {CLASS_CHECK_TRAP, " = 1"})
    public Object[] test117() {
        // TODO 8251971 Use get_val_class() here
        return Arrays.copyOf((Object[])get_obj_src(), 8, val_src2.getClass());
    }

    @Run(test = "test117")
    public void test117_verifier() {
        Object[] res = test117();
        verify(obj_src, res);
    }

    @Test
    @IR(counts = {CLASS_CHECK_TRAP, " = 1"})
    public Object[] test117_null() {
        // TODO 8251971 Use get_val_class() here
        return Arrays.copyOf((Object[])get_obj_null_src(), 8, val_src2.getClass());
    }

    @Run(test = "test117_null")
    public void test117_null_verifier() {
        Object[] res = test117_null();
        verify((Object[])get_obj_null_src(), res);
    }

    // Some more Arrays.copyOf tests with only constant class

    @Test
    @IR(counts = {CLASS_CHECK_TRAP, "= 1"})
    // TODO 8251971
    // failOn = INTRINSIC_SLOW_PATH)
    public Object[] test118(Object[] src) {
        return Arrays.copyOf(src, 8, val_src.getClass());
    }

    @Run(test = "test118")
    public void test118_verifier() {
        Object[] res = test118(obj_src);
        verify(obj_src, res);
        res = test118(val_src);
        verify(val_src, res);
        res = test118(obj_null_src);
        verify(obj_null_src, res);
    }

    @Test
    public Object[] test119(Object[] src) {
        return Arrays.copyOf(src, 8, Object[].class);
    }

    @Run(test = "test119")
    public void test119_verifier() {
        Object[] res = test119(obj_src);
        verify(obj_src, res);
        res = test119(val_src);
        verify(val_src, res);
    }

    @Test
    @IR(counts = {CLASS_CHECK_TRAP, "= 1"})
    // TODO 8251971
    // failOn = INTRINSIC_SLOW_PATH)
    public Object[] test120(Object[] src) {
        return Arrays.copyOf(src, 8, NonValueClass[].class);
    }

    @Run(test = "test120")
    public void test120_verifier() {
        NonValueClass[] arr = new NonValueClass[8];
        for (int i = 0; i < 8; ++i) {
            arr[i] = new NonValueClass(rI + i);
        }
        Object[] res = test120(arr);
        verify(arr, res);
        try {
            test120(val_src);
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException e) {
            // expected
        }
    }

    @Test
    public Object[] test121(Object[] src) {
        return Arrays.copyOf(src, 8, val_src.getClass());
    }

    @Run(test = "test121")
    @Warmup(10000) // Make sure we hit too_many_traps for the src <: dst check
    public void test121_verifier() {
        Object[] res = test121(obj_src);
        verify(obj_src, res);
        res = test121(val_src);
        verify(val_src, res);
        res = test121(obj_null_src);
        verify(obj_null_src, res);
    }
    @Test
    public Object[] test122(Object[] src) {
        return Arrays.copyOf(src, 8, get_val_class());
    }

    @Run(test = "test122")
    @Warmup(10000) // Make sure we hit too_many_traps for the src <: dst check
    public void test122_verifier() {
        Object[] res = test122(obj_src);
        verify(obj_src, res);
        res = test122(val_src);
        verify(val_src, res);
        res = test122(obj_null_src);
        verify(obj_null_src, res);
    }

    @Test
    public Object[] test123(Object[] src) {
        return Arrays.copyOf(src, 8, NonValueClass[].class);
    }

    @Run(test = "test123")
    @Warmup(10000) // Make sure we hit too_many_traps for the src <: dst check
    public void test123_verifier() {
        NonValueClass[] arr = new NonValueClass[8];
        for (int i = 0; i < 8; ++i) {
            arr[i] = new NonValueClass(rI + i);
        }
        Object[] res = test123(arr);
        verify(arr, res);
        try {
            test123(val_src);
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException e) {
            // expected
        }
    }

    @Test
    public Object[] test124(Object[] src) {
        return Arrays.copyOf(src, 8, get_non_val_class());
    }

    @Run(test = "test124")
    @Warmup(10000) // Make sure we hit too_many_traps for the src <: dst check
    public void test124_verifier() {
        NonValueClass[] arr = new NonValueClass[8];
        for (int i = 0; i < 8; ++i) {
            arr[i] = new NonValueClass(rI + i);
        }
        Object[] res = test124(arr);
        verify(arr, res);
        try {
            test124(val_src);
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException e) {
            // expected
        }
    }

    @Test
    public Object[] test125(Object[] src, Class klass) {
        return Arrays.copyOf(src, 8, klass);
    }

    @Run(test = "test125")
    @Warmup(10000) // Make sure we hit too_many_traps for the src <: dst check
    public void test125_verifier() {
        NonValueClass[] arr = new NonValueClass[8];
        for (int i = 0; i < 8; ++i) {
            arr[i] = new NonValueClass(rI + i);
        }
        Object[] res = test125(arr, NonValueClass[].class);
        verify((Object[])arr, res);
        res = test125(val_src, val_src.getClass());
        verify(val_src, res);
        res = test125(obj_src, val_src.getClass());
        verify(obj_src, res);
        res = test125(obj_null_src, val_src.getClass());
        verify(obj_null_src, res);
        try {
            test125(arr, val_src.getClass());
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException e) {
            // expected
        }
        try {
            test125(val_src, MyValue1[].class);
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException e) {
            // expected
        }
    }

    // Verify that clone from (flat) value class array not containing oops is always optimized.
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {JLONG_ARRAYCOPY, "= 1"},
        failOn = {CHECKCAST_ARRAYCOPY, CLONE_INTRINSIC_SLOW_PATH})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        failOn = CLONE_INTRINSIC_SLOW_PATH)
    public Object[] test126() {
        return val_src.clone();
    }

    @Run(test = "test126")
    public void test126_verifier() {
        Object[] res = test126();
        verify(val_src, res);
    }

    // Verify correctness of generic_copy stub
    @Test
    public void test127(Object src, Object dst, int len) {
        System.arraycopy(src, 0, dst, 0, len);
    }

    @Run(test = "test127")
    public void test127_verifier() {
        test127(val_src, obj_dst, 8);
        verify(val_src, obj_dst);
        test127(val_src, val_dst, 8);
        verify(val_src, val_dst);
        test127(obj_src, val_dst, 8);
        verify(obj_src, val_dst);
        try {
            test127(obj_null_src, val_dst, 8);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // expected
        }
    }

    static value class Test127aValue {
        int x;

        Test127aValue(int x) {
            this.x = x;
        }

        public String toString() {
            return "x: " + x;
        }
    }

    static Test127aValue valueTest127a = new Test127aValue(34);
    static final Test127aValue[] srcTest127a = (Test127aValue[])ValueClass.newNullRestrictedNonAtomicArray(Test127aValue.class, 8, valueTest127a);
    static final Test127aValue[] destTest127a = (Test127aValue[])ValueClass.newNullRestrictedNonAtomicArray(Test127aValue.class, 8, valueTest127a);


    @Test
    public void test127a(int srcPos, int destPos, int len) {
        System.arraycopy(srcTest127a, srcPos, destTest127a, destPos, len);
    }

    // Ensure that System.arraycopy() is working properly with COH.
    @Run(test = "test127a")
    @Warmup(10000)
    public void test127a_verifier() {
        test127a(0,1, 7);
        for (int i = 0; i < 7; ++i) {
            Asserts.assertEQ(srcTest127a[i], destTest127a[i + 1]);
        }
        Asserts.assertEQ(valueTest127a, destTest127a[0]);
    }

    static void verifyTest127a(Object[] src, Object[] dst, int len, int offset) {
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(src[i], dst[i + offset]);
        }
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(src[i], dst[i + offset]);
        }
    }

    // Verify that copyOf with known source and unknown destination class is optimized
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {JLONG_ARRAYCOPY, "= 1"},
        failOn = CHECKCAST_ARRAYCOPY)
    public Object[] test128(Class klass) {
        return Arrays.copyOf(val_src, 8, klass);
    }

    @Run(test = "test128")
    public void test128_verifier() {
        Object[] res = test128(MyValue2[].class);
        verify(val_src, res);
        res = test128(Object[].class);
        verify(val_src, res);
        try {
            test128(MyValue1[].class);
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException e) {
            // expected
        }
    }

    // Arraycopy with non-array src/dst
    @Test
    public void test129(Object src, Object dst, int len) {
        System.arraycopy(src, 0, dst, 0, len);
    }

    @Run(test = "test129")
    public void test129_verifier() {
        try {
            test129(new Object(), new Object[0], 0);
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException e) {
            // expected
        }
        try {
            test129(new Object[0], new Object(), 0);
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException e) {
            // expected
        }
    }

    @NullRestricted
    static final MyValueEmpty empty = new MyValueEmpty();

    // Empty value class array access
    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        failOn = {LOAD_OF_ANY_KLASS})
    public MyValueEmpty test130(MyValueEmpty[] array) {
        array[0] = new MyValueEmpty();
        return array[1];
    }

    @Run(test = "test130")
    public void test130_verifier() {
        MyValueEmpty[] array = (MyValueEmpty[])ValueClass.newNullRestrictedNonAtomicArray(MyValueEmpty.class, 2, empty);
        MyValueEmpty res = test130(array);
        Asserts.assertEquals(array[0], empty);
        Asserts.assertEquals(res, empty);
    }

    @LooselyConsistentValue
    static value class EmptyContainer {
        @NullRestricted
        MyValueEmpty empty = new MyValueEmpty();
    }

    // Empty value class container array access
    @Test
    @IR(failOn = {LOAD_OF_ANY_KLASS})
    public MyValueEmpty test131(EmptyContainer[] array) {
        array[0] = new EmptyContainer();
        return array[1].empty;
    }

    @Run(test = "test131")
    public void test131_verifier() {
        EmptyContainer[] array = (EmptyContainer[])ValueClass.newNullRestrictedNonAtomicArray(EmptyContainer.class, 2, new EmptyContainer());
        MyValueEmpty res = test131(array);
        Asserts.assertEquals(array[0], new EmptyContainer());
        Asserts.assertEquals(res, new MyValueEmpty());
    }

    // Empty value class array access with unknown array type
    @Test
    public Object test132(Object[] array) {
        array[0] = new MyValueEmpty();
        return array[1];
    }

    @Run(test = "test132")
    public void test132_verifier() {
        Object[] array = (MyValueEmpty[])ValueClass.newNullRestrictedNonAtomicArray(MyValueEmpty.class, 2, empty);
        Object res = test132(array);
        Asserts.assertEquals(array[0], empty);
        Asserts.assertEquals(res, empty);
        array = new Object[2];
        res = test132(array);
        Asserts.assertEquals(array[0], empty);
        Asserts.assertEquals(res, null);
    }

    // Empty value class container array access with unknown array type
    @Test
    public Object test133(Object[] array) {
        array[0] = new EmptyContainer();
        return array[1];
    }

    @Run(test = "test133")
    public void test133_verifier() {
        EmptyContainer empty = new EmptyContainer();
        Object[] array = (EmptyContainer[])ValueClass.newNullRestrictedNonAtomicArray(EmptyContainer.class, 2, new EmptyContainer());
        Object res = test133(array);
        Asserts.assertEquals(array[0], empty);
        Asserts.assertEquals(res, empty);
        array = new Object[2];
        res = test133(array);
        Asserts.assertEquals(array[0], empty);
        Asserts.assertEquals(res, null);
    }

    // Non-escaping empty value class array access
    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public static MyValueEmpty test134() {
        MyValueEmpty[] array = new MyValueEmpty[1];
        array[0] = empty;
        return array[0];
    }

    @Run(test = "test134")
    public void test134_verifier() {
        MyValueEmpty res = test134();
        Asserts.assertEquals(res, empty);
    }

    // Test accessing a locked (value class) array
    @Test
    public Object test135(Object[] array, Object val) {
        array[0] = val;
        return array[1];
    }

    @Run(test = "test135")
    public void test135_verifier() {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        array1[1] = MyValue1.createWithFieldsInline(rI, rL);
        synchronized (array1) {
            Object res = test135(array1, array1[1]);
            Asserts.assertEquals(array1[1], res);
            Asserts.assertEquals(array1[0], array1[1]);
        }
        NonValueClass[] array2 = new NonValueClass[2];
        array2[1] = new NonValueClass(rI);
        synchronized (array2) {
            Object res = test135(array2, array2[1]);
            Asserts.assertEquals(res, array2[1]);
            Asserts.assertEquals(array2[0], array2[1]);
        }
    }

    // Same as test135 but with locking in compiled method
    @Test
    public Object test136(Object[] array, Object val) {
        Object res = null;
        synchronized (array) {
            array[0] = val;
            res = array[1];
        }
        return res;
    }

    @Run(test = "test136")
    public void test136_verifier() {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        array1[1] = MyValue1.createWithFieldsInline(rI, rL);
        Object res = test136(array1, array1[1]);
        Asserts.assertEquals(array1[1], res);
        Asserts.assertEquals(array1[0], array1[1]);
        NonValueClass[] array2 = new NonValueClass[2];
        array2[1] = new NonValueClass(rI);
        res = test136(array2, array2[1]);
        Asserts.assertEquals(res, array2[1]);
        Asserts.assertEquals(array2[0], array2[1]);
    }

    Object oFld1, oFld2;

    // Test loop unwswitching with locked (value class) array accesses
    @Test
    public void test137(Object[] array1, Object[] array2) {
        for (int i = 0; i < array1.length; i++) {
            oFld1 = array1[i];
            oFld2 = array2[i];
        }
    }

    @Run(test = "test137")
    public void test137_verifier() {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 100, MyValue1.DEFAULT);
        Arrays.fill(array1, MyValue1.createWithFieldsInline(rI, rL));
        NonValueClass[] array2 = new NonValueClass[100];
        Arrays.fill(array2, new NonValueClass(rI));
        synchronized (array1) {
            test137(array1, array1);
            Asserts.assertEquals(oFld1, array1[0]);
            Asserts.assertEquals(oFld2, array1[0]);
            test137(array1, array2);
            Asserts.assertEquals(oFld1, array1[0]);
            Asserts.assertEquals(oFld2, array2[0]);
            test137(array2, array1);
            Asserts.assertEquals(oFld1, array2[0]);
            Asserts.assertEquals(oFld2, array1[0]);
        }
        synchronized (array2) {
            test137(array2, array2);
            Asserts.assertEquals(oFld1, array2[0]);
            Asserts.assertEquals(oFld2, array2[0]);
            test137(array1, array2);
            Asserts.assertEquals(oFld1, array1[0]);
            Asserts.assertEquals(oFld2, array2[0]);
            test137(array2, array1);
            Asserts.assertEquals(oFld1, array2[0]);
            Asserts.assertEquals(oFld2, array1[0]);
        }
    }

    // Same as test137 but with locking in loop
    @Test
    public void test138(Object[] array1, Object[] array2) {
        for (int i = 0; i < array1.length; i++) {
            synchronized (array1) {
                oFld1 = array1[i];
            }
            synchronized (array2) {
                oFld2 = array2[i];
            }
        }
    }

    @Run(test = "test138")
    public void test138_verifier() {
        MyValue1[] array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 100, MyValue1.DEFAULT);
        Arrays.fill(array1, MyValue1.createWithFieldsInline(rI, rL));
        NonValueClass[] array2 = new NonValueClass[100];
        Arrays.fill(array2, new NonValueClass(rI));
        test138(array1, array1);
        Asserts.assertEquals(oFld1, array1[0]);
        Asserts.assertEquals(oFld2, array1[0]);
        test138(array1, array2);
        Asserts.assertEquals(oFld1, array1[0]);
        Asserts.assertEquals(oFld2, array2[0]);
        test138(array2, array1);
        Asserts.assertEquals(oFld1, array2[0]);
        Asserts.assertEquals(oFld2, array1[0]);
        test138(array2, array2);
        Asserts.assertEquals(oFld1, array2[0]);
        Asserts.assertEquals(oFld2, array2[0]);
        Asserts.assertEquals(oFld2, array2[0]);
    }

    // Test load from array that is only known to be not a value class array after parsing
    @Test
    @IR(failOn = {ALLOC, ALLOC_ARRAY, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP, LOAD_UNKNOWN_INLINE,
                  STORE_UNKNOWN_INLINE, INLINE_ARRAY_NULL_GUARD})
    public Object test139() {
        Object[]  array = null;
        Object[] oarray = new NonValueClass[1];
        Object[] varray = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        for (int i = 0; i < 10; i++) {
            array = varray;
            varray = oarray;
        }
        return array[0];
    }

    @Run(test = "test139")
    public void test139_verifier() {
        Object res = test139();
        Asserts.assertEquals(res, null);
    }

    // Test store to array that is only known to be not a value class array after parsing
    @Test
    @IR(failOn = {ALLOC_ARRAY_OF_MYVALUE_KLASS, ALLOC, LOOP, LOAD_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP,
                  LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE, INLINE_ARRAY_NULL_GUARD})
    public Object[] test140(Object val) {
        Object[]  array = null;
        Object[] oarray = new NonValueClass[1];
        Object[] varray = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        for (int i = 0; i < 10; i++) {
            array = varray;
            varray = oarray;
        }
        array[0] = val;
        return array;
    }

    @Run(test = "test140")
    public void test140_verifier() {
        NonValueClass obj = new NonValueClass(rI);
        Object[] res = test140(obj);
        Asserts.assertEquals(res[0], obj);
        res = test140(null);
        Asserts.assertEquals(res[0], null);
    }

    // Test load from array that is only known to be not a value class array after parsing
    // TODO 8255938
    @Test
    // @IR(failOn = {ALLOC_G, ALLOC_ARRAY, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE, INLINE_ARRAY_NULL_GUARD})
    public Object test141() {
        Object[]  array = null;
        Object[] oarray = new NonValueClass[1];
        Object[] varray = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        for (int i = 0; i < 10; i++) {
            array = oarray;
            oarray = varray;
        }
        return array[0];
    }

    @Run(test = "test141")
    public void test141_verifier() {
        Object res = test141();
        Asserts.assertEquals(res, MyValue1.createDefaultInline());
    }

    // Test store to array that is only known to be not a value class array after parsing
    // TODO 8255938
    @Test
    // @IR(failOn = {ALLOC_ARRAY_OF_MYVALUE_KLASS, ALLOC_G, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP, LOAD_UNKNOWN_INLINE, STORE_UNKNOWN_INLINE, INLINE_ARRAY_NULL_GUARD})
    public Object[] test142(Object val) {
        Object[]  array = null;
        Object[] oarray = new NonValueClass[1];
        Object[] varray = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        for (int i = 0; i < 10; i++) {
            array = oarray;
            oarray = varray;
        }
        array[0] = val;
        return array;
    }

    @Run(test = "test142")
    public void test142_verifier(RunInfo info) {
        Object[] res = test142(MyValue1.createDefaultInline());
        Asserts.assertEquals(res[0], MyValue1.createDefaultInline());
        if (!info.isWarmUp()) {
            try {
                test142(null);
                throw new RuntimeException("Should throw NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    static interface MyInterface143 {
        public int hash();
    }

    static class MyObject143 implements MyInterface143 {
        public int hash() { return 42; }
    }

    volatile MyInterface143[] array143 = new MyObject143[1];
    int len143 = 0;

    volatile int vf = 0;

    // Test that triggers an anti dependency failure when array mark word is loaded from immutable memory
    @Test
    public void test143() {
        MyInterface143[] arr = array143;
        int tmp = arr.length;
        for (int i = 0; i < len143; i++) {
            if (arr[i].hash() > 0) {
                return;
            }
        }
    }

    @Run(test = "test143")
    @Warmup(0)
    public void test143_verifier() {
        test143();
    }

    // Same as test143 but with two flat array checks that are unswitched
    @Test
    public void test144() {
        MyInterface143[] arr1 = array143;
        MyInterface143[] arr2 = array143;
        int tmp1 = arr1.length;
        int tmp2 = arr2.length;
        for (int i = 0; i < len143; i++) {
            if (arr1[i].hash() > 0 && arr2[i].hash() > 0) {
                return;
            }
        }
    }

    @Run(test = "test144")
    @Warmup(0)
    public void test144_verifier() {
        test144();
    }

    // Test that array load slow path correctly initializes non-flattened field of empty value class
    @Test
    public Object test145(Object[] array) {
        return array[0];
    }

    @Run(test = "test145")
    public void test145_verifier() {
        Object[] array = (EmptyContainer[])ValueClass.newNullRestrictedNonAtomicArray(EmptyContainer.class, 1, new EmptyContainer());
        EmptyContainer empty = (EmptyContainer)test145(array);
        Asserts.assertEquals(empty, new EmptyContainer());
    }

    // Test that non-flattened array does not block scalarization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public void test146(boolean b) {
        MyValue2 vt = MyValue2.createWithFieldsInline(rI, rD);
        MyValue2[] array = { vt };
        if (b) {
            for (int i = 0; i < 10; ++i) {
                if (array != array) {
                    array = null;
                }
            }
        }
    }

    @Run(test = "test146")
    @Warmup(50000)
    public void test146_verifier() {
        test146(true);
    }

    // Test that non-flattened array does not block scalarization
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS})
    public int test147(boolean deopt) {
        // Both vt and array should be scalarized
        MyValue2 vt = MyValue2.createWithFieldsInline(rI, rD);
        MyValue2[] array = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 1, MyValue2.DEFAULT);

        // Delay scalarization to after loop opts
        boolean store = false;
        for (int i = 0; i < 5; ++i) {
            if (i == 1) {
                store = true;
            }
        }
        if (store) {
            array[0] = vt;
        }

        if (deopt) {
            // Uncommon trap referencing array
            return array[0].x + 42;
        }
        return array[0].x;
    }

    @Run(test = "test147")
    @Warmup(50000)
    public void test147_verifier(RunInfo info) {
        int res = test147(!info.isWarmUp());
        Asserts.assertEquals(res, MyValue2.createWithFieldsInline(rI, rD).x + (info.isWarmUp() ? 0 : 42));
    }

    // Test that correct basic types are used when folding field
    // loads from a scalar replaced array through an arraycopy.
    @Test
    public void test148(MyValue1 vt) {
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        MyValue1[] dst = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        src[0] = vt;
        System.arraycopy(src, 0, dst, 0, 1);
        if (src[0].hash() != dst[0].hash()) {
          throw new RuntimeException("Unexpected hash");
        }
    }

    @Run(test = "test148")
    public void test148_verifier() {
        test148(MyValue1.createWithFieldsInline(rI, rL));
    }

    // Abstract class without any value class implementers
    static abstract class MyAbstract149 {
        public abstract int get();
    }

    static class TestClass149 extends MyAbstract149 {
        final int x;

        public int get() { return x; };

        public TestClass149(int x) {
            this.x = x;
        }
    }

    // Test OSR compilation with array known to be not null-free/flat
    @Test
    public int test149(MyAbstract149[] array) {
        int res = 0;
        // Trigger OSR compilation
        for (int i = 0; i < 10_000; ++i) {
            res += array[i % 10].get();
        }
        return res;
    }

    @Run(test = "test149")
    public void test149_verifier() {
        TestClass149[] array = new TestClass149[10];
        for (int i = 0; i < 10; ++i) {
            array[i] = new TestClass149(i);
        }
        Asserts.assertEquals(test149(array), 45000);
    }

    @LooselyConsistentValue
    static value class Test150Value {
        Object s;

        public Test150Value(String s) {
            this.s = s;
        }
    }

    // Test that optimizing a checkcast of a load from a flat array works as expected
    @Test
    static String test150(String s) {
        Test150Value[] array = (Test150Value[])ValueClass.newNullRestrictedNonAtomicArray(Test150Value.class, 1, new Test150Value("test"));
        array[0] = new Test150Value(s);
        return (String)array[0].s;
    }

    @Run(test = "test150")
    public void test150_verifier() {
        Asserts.assertEquals(test150("bla"), "bla");
    }

    static value class Test151Value {
        byte b;
        String s;

        Test151Value(byte b, String s) {
            this.b = b;
            this.s = s;
        }

        static final Test151Value DEFAULT = new Test151Value((byte) 1, "hello");

        static final Test151Value[] ARRAY = (Test151Value[]) ValueClass.newNullRestrictedAtomicArray(Test151Value.class, 100, DEFAULT);
    }

    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS,})
    static Test151Value test151(int i) {
        return Test151Value.ARRAY[i];
    }

    @Run(test = "test151")
    public void test151_verifier() {
        Asserts.assertEquals(Test151Value.DEFAULT, test151(rI & 15));
    }

    // Make sure this can't be flattened
    static value class MyValue152Inline {
        long l1 = rL;
        long l2 = rL;
    }

    @LooselyConsistentValue
    static value class MyValue152 {
        double d = rD;

        @NullRestricted
        MyValue152Inline val = new MyValue152Inline(); // Not flat
    }

    // Test that EA works for null-free arrays
    @Test
    // TODO 8350865 Scalar replacement does not work well for flat arrays
    //@IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
    //    failOn = {ALLOC, ALLOCA})
    public MyValue152 test152() {
        MyValue152[] array = (MyValue152[])ValueClass.newNullRestrictedNonAtomicArray(MyValue152.class, 1, new MyValue152());
        return array[0];
    }

    @Run(test = "test152")
    public void test152_verifier() {
        Asserts.assertEquals(test152(), new MyValue152());
    }

    @LooselyConsistentValue
    static value class MyValue153 {
        @NullRestricted
        MyValue152Inline val = new MyValue152Inline(); // Not flat
    }

    // Same as test152 but triggers a slightly different asserts
    @Test
    // TODO 8350865 Scalar replacement does not work well for flat arrays
    //@IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
    //    failOn = {ALLOC, ALLOCA})
    public MyValue153 test153() {
        MyValue153[] array = (MyValue153[])ValueClass.newNullRestrictedNonAtomicArray(MyValue153.class, 1, new MyValue153());
        return array[0];
    }

    @Run(test = "test153")
    public void test153_verifier() {
        Asserts.assertEquals(test153(), new MyValue153());
    }

    // Same as test152 but triggers an incorrect result
    @Test
    // TODO 8350865 Scalar replacement does not work well for flat arrays
    //@IR(failOn = {ALLOC, ALLOCA_G, LOAD, STORE, TRAP})
    public double test154() {
        MyValue152[] array = (MyValue152[])ValueClass.newNullRestrictedNonAtomicArray(MyValue152.class, 1, new MyValue152());
        return array[0].d;
    }

    @Run(test = "test154")
    public void test154_verifier() {
        Asserts.assertEquals(test154(), rD);
    }

    // When accessing into an array, we can speculate on the exact type of the array. If the
    // speculative assumption holds, we can elide all checks on properties of the array (flatness,
    // atomicity, nullability).
    @Test
    @IR(applyIf = {"UseArrayLoadStoreProfile", "true"},
        failOn = {IRNode.MEMBAR}, counts = {IRNode.IF, "3"}) // null check, class check, range check
    static int test155(Test151Value[] a) {
        return a[0].b;
    }

    @Run(test = "test155")
    public void test155_verifier() {
        test155((Test151Value[])ValueClass.newNullRestrictedNonAtomicArray(Test151Value.class, 1, Test151Value.DEFAULT));
    }
}
