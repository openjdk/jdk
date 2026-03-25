/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_ARRAY_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.ALLOC_OF_MYVALUE_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.LOAD_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.LOAD_UNKNOWN_INLINE;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_OF_ANY_KLASS;
import static compiler.valhalla.inlinetypes.InlineTypeIRNode.STORE_UNKNOWN_INLINE;
import static compiler.valhalla.inlinetypes.InlineTypes.rI;
import static compiler.valhalla.inlinetypes.InlineTypes.rL;
import static compiler.valhalla.inlinetypes.InlineTypes.rD;

import static compiler.lib.ir_framework.IRNode.ALLOC;
import static compiler.lib.ir_framework.IRNode.LOOP;
import static compiler.lib.ir_framework.IRNode.PREDICATE_TRAP;
import static compiler.lib.ir_framework.IRNode.UNSTABLE_IF_TRAP;

import jdk.internal.value.ValueClass;

import java.lang.reflect.Method;
import java.util.Arrays;

/*
 * @test
 * @key randomness
 * @summary Test nullable value class arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestNullableArrays 0
 */

/*
 * @test
 * @key randomness
 * @summary Test nullable value class arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestNullableArrays 1
 */

/*
 * @test
 * @key randomness
 * @summary Test nullable value class arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestNullableArrays 2
 */

/*
 * @test
 * @key randomness
 * @summary Test nullable value class arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestNullableArrays 3
 */

/*
 * @test
 * @key randomness
 * @summary Test nullable value class arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestNullableArrays 4
 */

/*
 * @test
 * @key randomness
 * @summary Test nullable value class arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestNullableArrays 5
 */

/*
 * @test
 * @key randomness
 * @summary Test nullable value class arrays.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestNullableArrays 6
 */

@ForceCompileClassInitializer
public class TestNullableArrays {

    public static void main(String[] args) {

        Scenario[] scenarios = InlineTypes.DEFAULT_SCENARIOS;
        scenarios[2].addFlags("-XX:-MonomorphicArrayCheck", "-XX:-UncommonNullCast", "-XX:+StressArrayCopyMacroNode");
        scenarios[3].addFlags("-XX:-MonomorphicArrayCheck", "-XX:-UncommonNullCast");
        scenarios[4].addFlags("-XX:-MonomorphicArrayCheck", "-XX:-UncommonNullCast");
        scenarios[5].addFlags("-XX:-MonomorphicArrayCheck", "-XX:-UncommonNullCast", "-XX:+StressArrayCopyMacroNode");

        InlineTypes.getFramework()
                   .addScenarios(scenarios[Integer.parseInt(args[0])])
                   .addHelperClasses(MyValue1.class,
                                     MyValue2.class,
                                     MyValue2Inline.class)
                   .start();
    }

    static {
        // Make sure RuntimeException is loaded to prevent uncommon traps in IR verified tests
        RuntimeException tmp = new RuntimeException("42");
    }

    // Helper methods

    protected long hash() {
        return hash(rI, rL);
    }

    protected long hash(int x, long y) {
        return MyValue1.createWithFieldsInline(x, y).hash();
    }

    private static final MyValue1 testValue1 = MyValue1.createWithFieldsInline(rI, rL);

    // Test nullable value class array creation and initialization
    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        counts = {ALLOC_ARRAY_OF_MYVALUE_KLASS, "= 1"})
    @IR(applyIf = {"UseArrayFlattening", "false"},
        counts = {ALLOC_ARRAY_OF_MYVALUE_KLASS, "= 1"},
        failOn = {LOAD_OF_ANY_KLASS})
    public MyValue1[] test1(int len) {
        MyValue1[] va = new MyValue1[len];
        if (len > 0) {
            va[0] = null;
        }
        for (int i = 1; i < len; ++i) {
            va[i] = MyValue1.createWithFieldsDontInline(rI, rL);
        }
        return va;
    }

    @Run(test = "test1")
    public void test1_verifier() {
        int len = Math.abs(rI % 10);
        MyValue1[] va = test1(len);
        if (len > 0) {
            Asserts.assertEQ(va[0], null);
        }
        for (int i = 1; i < len; ++i) {
            Asserts.assertEQ(va[i].hash(), hash());
        }
    }

    // Test creation of a value class array and element access
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test2() {
        MyValue1[] va = new MyValue1[1];
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
            if (va[i] != null) {
                result += va[i].hash();
            }
            va[i] = MyValue1.createWithFieldsInline(rI + 1, rL + 1);
        }
        va[0] = null;
        return result;
    }

    @Run(test = "test3")
    public void test3_verifier() {
        MyValue1[] va = new MyValue1[10];
        long expected = 0;
        for (int i = 1; i < 10; ++i) {
            va[i] = MyValue1.createWithFieldsDontInline(rI + i, rL + i);
            expected += va[i].hash();
        }
        long result = test3(va);
        Asserts.assertEQ(expected, result);
        Asserts.assertEQ(va[0], null);
        for (int i = 1; i < 10; ++i) {
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
        MyValue1[] va = new MyValue1[10];
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
            va = new MyValue1[5];
            for (int i = 0; i < 5; ++i) {
                va[i] = MyValue1.createWithFieldsInline(rI, rL);
            }
            va[4] = null;
        } else {
            va = new MyValue1[10];
            for (int i = 0; i < 10; ++i) {
                va[i] = MyValue1.createWithFieldsInline(rI + i, rL + i);
            }
            va[9] = null;
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
        for (int i = 1; i < 4; ++i) {
            Asserts.assertEQ(va[i].hash(), hash());
        }
        Asserts.assertEQ(va[4], null);
        va = test5(false);
        Asserts.assertEQ(va.length, 10);
        Asserts.assertEQ(va[0].hash(), hash(rI + 1, hash(rI, rL) + 1));
        for (int i = 1; i < 9; ++i) {
            Asserts.assertEQ(va[i].hash(), hash(rI + i, rL + i));
        }
        Asserts.assertEQ(va[9], null);
    }

    // Test creation of value class array with single element
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue1 test6() {
        MyValue1[] va = new MyValue1[1];
        return va[0];
    }

    @Run(test = "test6")
    public void test6_verifier() {
        MyValue1[] va = new MyValue1[1];
        MyValue1 v = test6();
        Asserts.assertEQ(v, null);
    }

    // Test initialization of value class arrays
    @Test
    @IR(failOn = {LOAD_OF_ANY_KLASS})
    public MyValue1[] test7(int len) {
        return new MyValue1[len];
    }

    @Run(test = "test7")
    public void test7_verifier() {
        int len = Math.abs(rI % 10);
        MyValue1[] va = test7(len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(va[i], null);
            va[i] = null;
        }
    }

    // Test creation of value class array with zero length
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue1[] test8() {
        return new MyValue1[0];
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
        test9_va = new MyValue1[1];
        test9_va[0] = testValue1;
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
                    if (k == 0) {
                        arr[i][j][k] = null;
                    }
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
                    if (k == 0) {
                        Asserts.assertEQ(null, arr[i][j][k]);
                    } else {
                        Asserts.assertEQ(MyValue1.createWithFieldsDontInline(rI + i , rL + j + k), arr[i][j][k]);
                    }
                    arr[i][j][k] = null;
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
                    if (arr[i][j][k] != null) {
                        res[l] = arr[i][j][k].hash();
                    }
                    arr[i][j][k] = null;
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
                    if (j != 2) {
                        arr[i][j][k] = MyValue1.createWithFieldsDontInline(rI + i, rL + j + k);
                        verif[l] = arr[i][j][k].hash();
                    }
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
        MyValue1[] va = new MyValue1[arraySize];

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
        MyValue1[] va = new MyValue1[arraySize];

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
        MyValue1[] va = new MyValue1[arraySize];

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
        MyValue1[] va = new MyValue1[arraySize];

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
        MyValue1[] va = new MyValue1[arraySize];

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
        MyValue1[] va = new MyValue1[arraySize];

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
    public void test18_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue1[] va1 = new MyValue1[len];
        MyValue1[] va2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 1; i < len; ++i) {
            va1[i] = testValue1;
            va2[i] = testValue1;
        }
        MyValue1[] result1 = test18(va1);
        if (len > 0) {
            Asserts.assertEQ(result1[0], null);
        }
        for (int i = 1; i < len; ++i) {
            Asserts.assertEQ(va1[i], result1[i]);
        }
        // make sure we do deopt: GraphKit::new_array assumes an
        // array of references
        for (int j = 0; j < 10; j++) {
            MyValue1[] result2 = test18(va2);

            for (int i = 0; i < len; ++i) {
                Asserts.assertEQ(va2[i], result2[i]);
            }
        }
        if (compile_and_run_again_if_deoptimized(info)) {
            MyValue1[] result2 = test18(va2);
            for (int i = 0; i < len; ++i) {
                Asserts.assertEQ(va2[i], result2[i]);
            }
        }
    }

    // clone() as series of loads/stores
    static MyValue1[] test19_orig = null;

    @Test
    public MyValue1[] test19() {
        MyValue1[] va = new MyValue1[8];
        for (int i = 1; i < va.length; ++i) {
            va[i] = MyValue1.createWithFieldsInline(rI, rL);
        }
        test19_orig = va;

        return va.clone();
    }

    @Run(test = "test19")
    public void test19_verifier() {
        MyValue1[] result = test19();
        Asserts.assertEQ(result[0], null);
        for (int i = 1; i < test19_orig.length; ++i) {
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
        MyValue1[] src1 = new MyValue1[len];
        MyValue1[] src2 = new MyValue1[len];
        MyValue1[] src3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] src4 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] dst1 = new MyValue1[len];
        MyValue1[] dst2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] dst3 = new MyValue1[len];
        MyValue1[] dst4 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        if (len > 0) {
            src2[0] = testValue1;
        }
        for (int i = 1; i < len; ++i) {
            src1[i] = testValue1;
            src2[i] = testValue1;
            src3[i] = testValue1;
            src4[i] = testValue1;
        }
        test20(src1, dst1);
        test20(src2, dst2);
        test20(src3, dst3);
        test20(src4, dst4);
        if (len > 0) {
            Asserts.assertEQ(null, dst1[0]);
            Asserts.assertEQ(src2[0], dst2[0]);
            Asserts.assertEQ(src3[0], dst3[0]);
            Asserts.assertEQ(src4[0], dst4[0]);
        }
        for (int i = 1; i < len; ++i) {
            Asserts.assertEQ(src1[i], dst1[i]);
            Asserts.assertEQ(src2[i], dst2[i]);
            Asserts.assertEQ(src3[i], dst3[i]);
            Asserts.assertEQ(src4[i], dst4[i]);
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
        MyValue2[] src1 = new MyValue2[len];
        MyValue2[] src2 = new MyValue2[len];
        MyValue2[] src3 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        MyValue2[] src4 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        MyValue2[] dst1 = new MyValue2[len];
        MyValue2[] dst2 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        MyValue2[] dst3 = new MyValue2[len];
        MyValue2[] dst4 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, len, MyValue2.DEFAULT);
        if (len > 0) {
            src2[0] = MyValue2.createWithFieldsInline(rI, rD);
        }
        for (int i = 1; i < len; ++i) {
            src1[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            src2[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            src3[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            src4[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test21(src1, dst1);
        test21(src2, dst2);
        test21(src3, dst3);
        test21(src4, dst4);
        if (len > 0) {
            Asserts.assertEQ(null, dst1[0]);
            Asserts.assertEQ(src2[0], dst2[0]);
            Asserts.assertEQ(src3[0], dst3[0]);
            Asserts.assertEQ(src4[0], dst4[0]);
        }
        for (int i = 1; i < len; ++i) {
            Asserts.assertEQ(src1[i], dst1[i]);
            Asserts.assertEQ(src2[i], dst2[i]);
            Asserts.assertEQ(src3[i], dst3[i]);
            Asserts.assertEQ(src4[i], dst4[i]);
        }
    }

    // arraycopy() of value class array with oop field and tightly
    // coupled allocation as dest
    @Test
    public MyValue1[] test22(MyValue1[] src) {
        MyValue1[] dst = new MyValue1[src.length];
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }

    @Run(test = "test22")
    public void test22_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] src1 = new MyValue1[len];
        MyValue1[] src2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 1; i < len; ++i) {
            src1[i] = testValue1;
            src2[i] = testValue1;
        }
        MyValue1[] dst1 = test22(src1);
        MyValue1[] dst2 = test22(src2);
        if (len > 0) {
            Asserts.assertEQ(null, dst1[0]);
            Asserts.assertEQ(MyValue1.createDefaultInline(), dst2[0]);
        }
        for (int i = 1; i < len; ++i) {
            Asserts.assertEQ(src1[i], dst1[i]);
            Asserts.assertEQ(src2[i], dst2[i]);
        }
    }

    // arraycopy() of value class array with oop fields and tightly
    // coupled allocation as dest
    @Test
    public MyValue1[] test23(MyValue1[] src) {
        MyValue1[] dst = new MyValue1[src.length + 10];
        System.arraycopy(src, 0, dst, 5, src.length);
        return dst;
    }

    @Run(test = "test23")
    public void test23_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] src1 = new MyValue1[len];
        MyValue1[] src2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 0; i < len; ++i) {
            src1[i] = testValue1;
            src2[i] = testValue1;
        }
        MyValue1[] dst1 = test23(src1);
        MyValue1[] dst2 = test23(src2);
        for (int i = 0; i < 5; ++i) {
            Asserts.assertEQ(null, dst1[i]);
            Asserts.assertEQ(null, dst2[i]);
        }
        for (int i = 5; i < len; ++i) {
            Asserts.assertEQ(src1[i], dst1[i]);
            Asserts.assertEQ(src2[i], dst2[i]);
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
        MyValue1[] src1 = new MyValue1[len];
        MyValue1[] src2 = new MyValue1[len];
        MyValue1[] src3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] src4 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] dst1 = new MyValue1[len];
        MyValue1[] dst2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        MyValue1[] dst3 = new MyValue1[len];
        MyValue1[] dst4 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        if (len > 0) {
            src2[0] = testValue1;
        }
        for (int i = 1; i < len; ++i) {
            src1[i] = testValue1;
            src2[i] = testValue1;
            src3[i] = testValue1;
            src4[i] = testValue1;
        }
        test24(src1, dst1);
        test24(src2, dst2);
        test24(src3, dst3);
        test24(src4, dst4);
        if (len > 0) {
            Asserts.assertEQ(null, dst1[0]);
            Asserts.assertEQ(src2[0], dst2[0]);
            Asserts.assertEQ(src3[0], dst3[0]);
            Asserts.assertEQ(src4[0], dst4[0]);
        }
        for (int i = 1; i < len; ++i) {
            Asserts.assertEQ(src1[i], dst1[i]);
            Asserts.assertEQ(src2[i], dst2[i]);
            Asserts.assertEQ(src3[i], dst3[i]);
            Asserts.assertEQ(src4[i], dst4[i]);
        }
    }

    // short arraycopy() with no oop field
    @Test
    public void test25(MyValue2[] src, MyValue2[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test25")
    public void test25_verifier() {
        MyValue2[] src1 = new MyValue2[8];
        MyValue2[] src2 = new MyValue2[8];
        MyValue2[] src3 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        MyValue2[] src4 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        MyValue2[] dst1 = new MyValue2[8];
        MyValue2[] dst2 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        MyValue2[] dst3 = new MyValue2[8];
        MyValue2[] dst4 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 8, MyValue2.DEFAULT);
        src2[0] = MyValue2.createWithFieldsInline(rI, rD);
        for (int i = 1; i < 8; ++i) {
            src1[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            src2[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            src3[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            src4[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test25(src1, dst1);
        test25(src2, dst2);
        test25(src3, dst3);
        test25(src4, dst4);
        Asserts.assertEQ(null, dst1[0]);
        Asserts.assertEQ(src2[0], dst2[0]);
        Asserts.assertEQ(src3[0], dst3[0]);
        Asserts.assertEQ(src4[0], dst4[0]);
        for (int i = 1; i < 8; ++i) {
            Asserts.assertEQ(src1[i], dst1[i]);
            Asserts.assertEQ(src2[i], dst2[i]);
            Asserts.assertEQ(src3[i], dst3[i]);
            Asserts.assertEQ(src4[i], dst4[i]);
        }
    }

    // short arraycopy() with oop fields
    @Test
    public void test26(MyValue1[] src, MyValue1[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test26")
    public void test26_verifier() {
        MyValue1[] src1 = new MyValue1[8];
        MyValue1[] src2 = new MyValue1[8];
        MyValue1[] src3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] src4 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] dst1 = new MyValue1[8];
        MyValue1[] dst2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] dst3 = new MyValue1[8];
        MyValue1[] dst4 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        src2[0] = testValue1;
        for (int i = 1; i < 8; ++i) {
            src1[i] = testValue1;
            src2[i] = testValue1;
            src3[i] = testValue1;
            src4[i] = testValue1;
        }
        test26(src1, dst1);
        test26(src2, dst2);
        test26(src3, dst3);
        test26(src4, dst4);
        Asserts.assertEQ(null, dst1[0]);
        Asserts.assertEQ(src2[0], dst2[0]);
        Asserts.assertEQ(src3[0], dst3[0]);
        Asserts.assertEQ(src4[0], dst4[0]);
        for (int i = 1; i < 8; ++i) {
            Asserts.assertEQ(src1[i], dst1[i]);
            Asserts.assertEQ(src2[i], dst2[i]);
            Asserts.assertEQ(src3[i], dst3[i]);
            Asserts.assertEQ(src4[i], dst4[i]);
        }
    }

    // short arraycopy() with oop fields and offsets
    @Test
    public void test27(MyValue1[] src, MyValue1[] dst) {
        System.arraycopy(src, 1, dst, 2, 6);
    }

    @Run(test = "test27")
    public void test27_verifier() {
        MyValue1[] src1 = new MyValue1[8];
        MyValue1[] src2 = new MyValue1[8];
        MyValue1[] src3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] src4 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] dst1 = new MyValue1[8];
        MyValue1[] dst2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        MyValue1[] dst3 = new MyValue1[8];
        MyValue1[] dst4 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        for (int i = 1; i < 8; ++i) {
            src1[i] = testValue1;
            src2[i] = testValue1;
            src3[i] = testValue1;
            src4[i] = testValue1;
        }
        test27(src1, dst1);
        test27(src2, dst2);
        test27(src3, dst3);
        test27(src4, dst4);
        for (int i = 0; i < 2; ++i) {
            Asserts.assertEQ(null, dst1[i]);
            Asserts.assertEQ(MyValue1.createDefaultInline(), dst2[i]);
            Asserts.assertEQ(null, dst3[i]);
            Asserts.assertEQ(MyValue1.createDefaultInline(), dst4[i]);
        }
        for (int i = 2; i < 8; ++i) {
            Asserts.assertEQ(src1[i], dst1[i]);
            Asserts.assertEQ(src2[i], dst2[i]);
            Asserts.assertEQ(src3[i], dst3[i]);
            Asserts.assertEQ(src4[i], dst4[i]);
        }
    }

    // non escaping allocations
    // TODO 8252027: Make sure this is optimized with ZGC
    @Test
    @IR(applyIf = {"UseZGC", "false"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue2 test28() {
        MyValue2[] src = new MyValue2[10];
        src[0] = null;
        MyValue2[] dst = (MyValue2[])src.clone();
        return dst[0];
    }

    @Run(test = "test28")
    public void test28_verifier() {
        MyValue2 v = MyValue2.createWithFieldsInline(rI, rD);
        MyValue2 result = test28();
        Asserts.assertEQ(result, null);
    }

    // non escaping allocations
    @Test
    // TODO 8372332: Add LOAD_OF_ANY_KLASS when return values are not scalarized
    @IR(failOn = {ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public MyValue2 test29(MyValue2[] src) {
        MyValue2[] dst = new MyValue2[10];
        System.arraycopy(src, 0, dst, 0, 10);
        return dst[0];
    }

    @Run(test = "test29")
    public void test29_verifier(RunInfo info) {
        MyValue2[] src1 = new MyValue2[10];
        MyValue2[] src2 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        for (int i = 0; i < 10; ++i) {
            src1[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            src2[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        MyValue2 v = test29(src1);
        Asserts.assertEQ(src1[0], v);
        if (!info.isWarmUp()) {
            v = test29(src2);
            Asserts.assertEQ(src2[0], v);
        }
    }

    // non escaping allocation with uncommon trap that needs
    // eliminated value class array element as debug info
    @Test
    public MyValue2 test30(MyValue2[] src, boolean flag) {
        MyValue2[] dst = new MyValue2[10];
        System.arraycopy(src, 0, dst, 0, 10);
        if (flag) { }
        return dst[0];
    }

    @Run(test = "test30")
    @Warmup(10000)
    public void test30_verifier(RunInfo info) {
        MyValue2[] src1 = new MyValue2[10];
        MyValue2[] src2 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 10, MyValue2.DEFAULT);
        for (int i = 0; i < 10; ++i) {
            src1[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
            src2[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        MyValue2 v = test30(src1, !info.isWarmUp());
        Asserts.assertEQ(src1[0], v);
        if (!info.isWarmUp()) {
            v = test30(src2, true);
            Asserts.assertEQ(src2[0], v);
        }
    }

    // non escaping allocation with memory phi
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test31(boolean b, boolean deopt, Method m) {
        MyValue2[] src = new MyValue2[1];
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
        MyValue2 v2 = MyValue2.createWithFieldsInline(rI+1, rD+1);
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
        MyValue1[] va1 = new MyValue1[len];
        MyValue1[] va2 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, len, MyValue1.DEFAULT);
        for (int i = 1; i < len; ++i) {
            va1[i] = testValue1;
            va2[i] = testValue1;
        }
        MyValue1[] result1 = (MyValue1[])test32(va1);
        MyValue1[] result2 = (MyValue1[])test32(va2);
        if (len > 0) {
            Asserts.assertEQ(null, result1[0]);
            Asserts.assertEQ(MyValue1.createDefaultInline(), result2[0]);
        }
        for (int i = 1; i < len; ++i) {
            Asserts.assertEQ(va1[i], result1[i]);
            Asserts.assertEQ(va2[i], result2[i]);
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
            va[i] = testValue1;
        }
        Object[] result = test33(va);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(va[i], result[i]);
        }
    }

    // clone() as series of loads/stores
    static Object[] test34_orig = null;

    @ForceInline
    public Object[] test34_helper(boolean flag) {
        Object[] va = null;
        if (flag) {
            va = new MyValue1[8];
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
        }
        if (compile_and_run_again_if_deoptimized(info)) {
            Object[] result = test34(true);
            verify(test34_orig, result);
        }
    }

    static void verify(Object[] src, Object[] dst) {
        for (int i = 0; i < src.length; ++i) {
            Asserts.assertEQ(src[i], dst[i]);
        }
    }

    static void verify(MyValue1[] src, MyValue1[] dst) {
        for (int i = 0; i < src.length; ++i) {
            Asserts.assertEQ(src[i], dst[i]);
        }
    }

    static void verify(MyValue1[] src, Object[] dst) {
        for (int i = 0; i < src.length; ++i) {
            Asserts.assertEQ(src[i], dst[i]);
        }
    }

    static void verify(MyValue2[] src, MyValue2[] dst) {
        for (int i = 0; i < src.length; ++i) {
            Asserts.assertEQ(src[i], dst[i]);
        }
    }

    static void verify(MyValue2[] src, Object[] dst) {
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

    // arraycopy() of value class array of unknown size
    @Test
    public void test35(Object src, Object dst, int len) {
        System.arraycopy(src, 0, dst, 0, len);
    }

    @Run(test = "test35")
    public void test35_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue1[] src = new MyValue1[len];
        MyValue1[] dst = new MyValue1[len];
        for (int i = 1; i < len; ++i) {
            src[i] = testValue1;
        }
        test35(src, dst, src.length);
        verify(src, dst);
        if (compile_and_run_again_if_deoptimized(info)) {
            test35(src, dst, src.length);
            verify(src, dst);
        }
    }

    @Test
    public void test36(Object src, MyValue2[] dst) {
        System.arraycopy(src, 0, dst, 0, dst.length);
    }

    @Run(test = "test36")
    public void test36_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue2[] src = new MyValue2[len];
        MyValue2[] dst = new MyValue2[len];
        for (int i = 1; i < len; ++i) {
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
        MyValue2[] src = new MyValue2[len];
        MyValue2[] dst = new MyValue2[len];
        for (int i = 1; i < len; ++i) {
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
        MyValue2[] dst = new MyValue2[len];
        for (int i = 1; i < len; ++i) {
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
            TestFramework.assertCompiled(m);
        }
    }

    @Test
    public void test39(MyValue2[] src, Object dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    @Run(test = "test39")
    public void test39_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue2[] src = new MyValue2[len];
        Object[] dst = new Object[len];
        for (int i = 1; i < len; ++i) {
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
        MyValue2[] dst = new MyValue2[len];
        for (int i = 1; i < len; ++i) {
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
            TestFramework.assertCompiled(m);
        }
    }

    @Test
    public void test41(Object src, Object[] dst) {
        System.arraycopy(src, 0, dst, 0, dst.length);
    }

    @Run(test = "test41")
    public void test41_verifier(RunInfo info) {
        int len = Math.abs(rI) % 10;
        MyValue2[] src = new MyValue2[len];
        Object[] dst = new Object[len];
        for (int i = 1; i < len; ++i) {
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
        for (int i = 1; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test42(src, dst);
        verify(src, dst);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiled(m);
        }
    }

    // short arraycopy()'s
    @Test
    public void test43(Object src, Object dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test43")
    public void test43_verifier(RunInfo info) {
        MyValue1[] src = new MyValue1[8];
        MyValue1[] dst = new MyValue1[8];
        for (int i = 1; i < 8; ++i) {
            src[i] = testValue1;
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
        MyValue2[] src = new MyValue2[8];
        MyValue2[] dst = new MyValue2[8];
        for (int i = 1; i < 8; ++i) {
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
        MyValue2[] src = new MyValue2[8];
        MyValue2[] dst = new MyValue2[8];
        for (int i = 1; i < 8; ++i) {
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
        MyValue2[] dst = new MyValue2[8];
        for (int i = 1; i < 8; ++i) {
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
            TestFramework.assertCompiled(m);
        }
    }

    @Test
    public void test47(MyValue2[] src, Object[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test47")
    public void test47_verifier(RunInfo info) {
        MyValue2[] src = new MyValue2[8];
        Object[] dst = new Object[8];
        for (int i = 1; i < 8; ++i) {
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
        MyValue2[] dst = new MyValue2[8];
        for (int i = 1; i < 8; ++i) {
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
            TestFramework.assertCompiled(m);
        }
    }

    @Test
    public void test49(Object src, Object[] dst) {
        System.arraycopy(src, 0, dst, 0, 8);
    }

    @Run(test = "test49")
    public void test49_verifier(RunInfo info) {
        MyValue2[] src = new MyValue2[8];
        Object[] dst = new Object[8];
        for (int i = 1; i < 8; ++i) {
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
        for (int i = 1; i < 8; ++i) {
            src[i] = MyValue2.createWithFieldsInline(rI+i, rD+i);
        }
        test50(src, dst);
        verify(src, dst);
        if (!info.isWarmUp()) {
            Method m = info.getTest();
            TestFramework.assertCompiled(m);
        }
    }

    @Test
    public MyValue1[] test51(MyValue1[] va) {
        return Arrays.copyOf(va, va.length, MyValue1[].class);
    }

    @Run(test = "test51")
    public void test51_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = new MyValue1[len];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
        }
        MyValue1[] result = test51(va);
        verify(va, result);
    }

    static final MyValue1[] test52_va = new MyValue1[8];

    @Test
    public MyValue1[] test52() {
        return Arrays.copyOf(test52_va, 8, MyValue1[].class);
    }

    @Run(test = "test52")
    public void test52_verifier() {
        for (int i = 1; i < 8; ++i) {
            test52_va[i] = testValue1;
        }
        MyValue1[] result = test52();
        verify(test52_va, result);
    }

    @Test
    public MyValue1[] test53(Object[] va) {
        return Arrays.copyOf(va, va.length, MyValue1[].class);
    }

    @Run(test = "test53")
    public void test53_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = new MyValue1[len];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
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
        MyValue1[] va = new MyValue1[len];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
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
        MyValue1[] va = new MyValue1[len];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
        }
        Object[] result = test55(va);
        verify(va, result);
    }

    @Test
    public MyValue1[] test56(Object[] va) {
        return Arrays.copyOf(va, va.length, MyValue1[].class);
    }

    @Run(test = "test56")
    public void test56_verifier() {
        int len = Math.abs(rI) % 10;
        Object[] va = new Object[len];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
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
        Object[] va = new MyValue1[len];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
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
        MyValue1[] va = new MyValue1[len];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
        }
        for (int i = 1; i < 10; i++) {
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
        return Arrays.copyOf(va, va.length+1, MyValue1[].class);
    }

    @Run(test = "test59")
    public void test59_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = new MyValue1[len];
        MyValue1[] verif = new MyValue1[len+1];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
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
        MyValue1[] va = new MyValue1[len];
        MyValue1[] verif = new MyValue1[len+1];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
            verif[i] = (MyValue1)va[i];
        }
        Object[] result = test60(va, MyValue1[].class);
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
        for (int i = 1; i < len; ++i) {
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
        MyValue1[] va = new MyValue1[len];
        NonValueClass[] oa = new NonValueClass[len];
        for (int i = 1; i < len; ++i) {
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
        MyValue1[] va = new MyValue1[len];
        MyValue1[] verif = new MyValue1[len+1];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
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
        return new MyValue1[8];
    }

    @Run(test = "test64")
    public void test64_verifier() {
        MyValue1[] va = test64();
        for (int i = 0; i < 8; ++i) {
            Asserts.assertEQ(va[i], null);
        }
    }

    // Test initialization of value class arrays: large array
    @Test
    public MyValue1[] test65() {
        return new MyValue1[32];
    }

    @Run(test = "test65")
    public void test65_verifier() {
        MyValue1[] va = test65();
        for (int i = 0; i < 32; ++i) {
            Asserts.assertEQ(va[i], null);
        }
    }

    // Check init store elimination
    @Test
    @IR(counts = {ALLOC_ARRAY_OF_MYVALUE_KLASS, "= 1"})
    public MyValue1[] test66(MyValue1 vt) {
        MyValue1[] va = new MyValue1[1];
        va[0] = vt;
        return va;
    }

    @Run(test = "test66")
    public void test66_verifier() {
        MyValue1 vt = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue1[] va = test66(vt);
        Asserts.assertEQ(vt, va[0]);
    }

    // Zeroing elimination and arraycopy
    @Test
    public MyValue1[] test67(MyValue1[] src) {
        MyValue1[] dst = new MyValue1[16];
        System.arraycopy(src, 0, dst, 0, 13);
        return dst;
    }

    @Run(test = "test67")
    public void test67_verifier() {
        MyValue1[] va = new MyValue1[16];
        MyValue1[] var = test67(va);
        for (int i = 0; i < 16; ++i) {
            Asserts.assertEQ(var[i], null);
        }
    }

    // A store with a zero value can be eliminated
    @Test
    public MyValue1[] test68() {
        MyValue1[] va = new MyValue1[2];
        va[0] = va[1];
        return va;
    }

    @Run(test = "test68")
    public void test68_verifier() {
        MyValue1[] va = test68();
        for (int i = 0; i < 2; ++i) {
            Asserts.assertEQ(va[i], null);
        }
    }

    // Requires individual stores to init array
    @Test
    public MyValue1[] test69(MyValue1 vt) {
        MyValue1[] va = new MyValue1[4];
        va[0] = vt;
        va[3] = vt;
        return va;
    }

    @Run(test = "test69")
    public void test69_verifier() {
        MyValue1 vt = MyValue1.createWithFieldsDontInline(rI, rL);
        MyValue1[] va = new MyValue1[4];
        va[0] = vt;
        va[3] = vt;
        MyValue1[] var = test69(vt);
        for (int i = 0; i < va.length; ++i) {
            Asserts.assertEQ(va[i], var[i]);
        }
    }

    // Same as test68 but store is further away from allocation
    @Test
    public MyValue1[] test70(MyValue1[] other) {
        other[1] = other[0];
        MyValue1[] va = new MyValue1[2];
        other[0] = va[1];
        va[0] = va[1];
        return va;
    }

    @Run(test = "test70")
    public void test70_verifier() {
        MyValue1[] va = new MyValue1[2];
        MyValue1[] var = test70(va);
        for (int i = 0; i < 2; ++i) {
            Asserts.assertEQ(va[i], var[i]);
        }
    }

    // EA needs to consider oop fields in flattened arrays
    @Test
    public void test71() {
        int len = 10;
        MyValue2[] src = new MyValue2[len];
        MyValue2[] dst = new MyValue2[len];
        for (int i = 1; i < len; ++i) {
            src[i] = MyValue2.createWithFieldsDontInline(rI+i, rD+i);
        }
        System.arraycopy(src, 0, dst, 0, src.length);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(src[i], dst[i]);
        }
    }

    @Run(test = "test71")
    public void test71_verifier() {
        test71();
    }

    // Test EA with leaf call to 'store_unknown_value'
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
        MyValue1[] arr = new MyValue1[3];
        try {
            test73(arr, v0, v1);
            throw new RuntimeException("ArrayStoreException expected");
        } catch (ArrayStoreException t) {
            // expected
        }
        Asserts.assertEQ(v0, arr[0]);
        Asserts.assertEQ(v1, arr[1]);
        Asserts.assertEQ(v1, arr[2]);
    }

    // Some more array clone tests
    @ForceInline
    public Object[] test74_helper(int i, MyValue1[] va, NonValueClass[] oa) {
        Object[] arr = null;
        if (i == 10) {
            arr = oa;
        } else {
            arr = va;
        }
        return arr;
    }

    @Test
    public Object[] test74(MyValue1[] va, NonValueClass[] oa) {
        int i = 0;
        for (; i < 10; i++);

        Object[] arr = test74_helper(i, va, oa);
        return arr.clone();
    }

    @Run(test = "test74")
    public void test74_verifier() {
        int len = Math.abs(rI) % 10;
        MyValue1[] va = new MyValue1[len];
        NonValueClass[] oa = new NonValueClass[len];
        for (int i = 1; i < len; ++i) {
            oa[i] = new NonValueClass(rI);
        }
        test74_helper(42, va, oa);
        Object[] result = test74(va, oa);

        for (int i = 0; i < va.length; ++i) {
            Asserts.assertEQ(oa[i], result[i]);
            // Check that array has correct properties (null-ok)
            result[i] = null;
        }
    }

    @ForceInline
    public Object[] test75_helper(int i, MyValue1[] va, NonValueClass[] oa) {
        Object[] arr = null;
        if (i == 10) {
            arr = va;
        } else {
            arr = oa;
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
        MyValue1[] va = new MyValue1[len];
        MyValue1[] verif = new MyValue1[len];
        for (int i = 1; i < len; ++i) {
            va[i] = testValue1;
            verif[i] = va[i];
        }
        NonValueClass[] oa = new NonValueClass[len];
        test75_helper(42, va, oa);
        Object[] result = test75(va, oa);
        verify(verif, result);
        if (len > 0) {
            // Check that array has correct properties (null-ok)
            result[0] = null;
        }
    }

    // Test mixing nullable and non-nullable arrays
    @Test
    public Object[] test76(MyValue1[] vva, MyValue1[] vba, MyValue1 vt, Object[] out, int n) {
        Object[] result = null;
        if (n == 0) {
            result = vva;
        } else if (n == 1) {
            result = vba;
        } else if (n == 2) {
            result = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 42, MyValue1.DEFAULT);
        } else if (n == 3) {
            result = new MyValue1[42];
        }
        result[0] = vt;
        out[0] = result[1];
        return result;
    }

    @Run(test = "test76")
    public void test76_verifier() {
        MyValue1 vt = testValue1;
        Object[] out = new Object[1];
        MyValue1[] vva = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 42, MyValue1.DEFAULT);
        MyValue1[] vva_r = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 42, MyValue1.DEFAULT);
        vva_r[0] = vt;
        MyValue1[] vba = new MyValue1[42];
        MyValue1[] vba_r = new MyValue1[42];
        vba_r[0] = vt;
        Object[] result = test76(vva, vba, vt, out, 0);
        verify(result, vva_r);
        Asserts.assertEQ(out[0], vva_r[1]);
        result = test76(vva, vba, vt, out, 1);
        verify(result, vba_r);
        Asserts.assertEQ(out[0], vba_r[1]);
        result = test76(vva, vba, vt, out, 2);
        verify(result, vva_r);
        Asserts.assertEQ(out[0], vva_r[1]);
        result = test76(vva, vba, vt, out, 3);
        verify(result, vba_r);
        Asserts.assertEQ(out[0], vba_r[1]);
    }

    @Test
    public Object[] test77(boolean b) {
        Object[] va;
        if (b) {
            va = new MyValue1[5];
            for (int i = 0; i < 5; ++i) {
                va[i] = testValue1;
            }
        } else {
            va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 10, MyValue1.DEFAULT);
            for (int i = 0; i < 10; ++i) {
                va[i] = MyValue1.createWithFieldsInline(rI + i, rL + i);
            }
        }
        long sum = ((MyValue1)va[0]).hashInterpreted();
        if (b) {
            va[0] = MyValue1.createWithFieldsDontInline(rI, sum);
        } else {
            va[0] = MyValue1.createWithFieldsDontInline(rI + 1, sum + 1);
        }
        return va;
    }

    @Run(test = "test77")
    public void test77_verifier() {
        Object[] va = test77(true);
        Asserts.assertEQ(va.length, 5);
        Asserts.assertEQ(((MyValue1)va[0]).hash(), hash(rI, hash()));
        for (int i = 1; i < 5; ++i) {
            Asserts.assertEQ(((MyValue1)va[i]).hash(), hash());
        }
        va = test77(false);
        Asserts.assertEQ(va.length, 10);
        Asserts.assertEQ(((MyValue1)va[0]).hash(), hash(rI + 1, hash(rI, rL) + 1));
        for (int i = 1; i < 10; ++i) {
            Asserts.assertEQ(((MyValue1)va[i]).hash(), hash(rI + i, rL + i));
        }
    }

    // Same as test76 but with non value class array cases
    @Test
    public Object[] test78(MyValue1[] vva, MyValue1[] vba, Object val, Object[] out, int n) {
        Object[] result = null;
        if (n == 0) {
            result = vva;
        } else if (n == 1) {
            result = vba;
        } else if (n == 2) {
            result = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 42, MyValue1.DEFAULT);
        } else if (n == 3) {
            result = new MyValue1[42];
        } else if (n == 4) {
            result = new NonValueClass[42];
        }
        result[0] = val;
        out[0] = result[1];
        return result;
    }

    @Run(test = "test78")
    public void test78_verifier() {
        MyValue1 vt = testValue1;
        NonValueClass obj = new NonValueClass(42);
        Object[] out = new Object[1];
        MyValue1[] vva = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 42, MyValue1.DEFAULT);
        MyValue1[] vva_r = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 42, MyValue1.DEFAULT);
        vva_r[0] = vt;
        MyValue1[] vba = new MyValue1[42];
        MyValue1[] vba_r = new MyValue1[42];
        vba_r[0] = vt;
        Object[] result = test78(vva, vba, vt, out, 0);
        verify(result, vva_r);
        Asserts.assertEQ(out[0], vva_r[1]);
        result = test78(vva, vba, vt, out, 1);
        verify(result, vba_r);
        Asserts.assertEQ(out[0], vba_r[1]);
        result = test78(vva, vba, vt, out, 2);
        verify(result, vva_r);
        Asserts.assertEQ(out[0], vva_r[1]);
        result = test78(vva, vba, vt, out, 3);
        verify(result, vba_r);
        Asserts.assertEQ(out[0], vba_r[1]);
        result = test78(vva, vba, obj, out, 4);
        Asserts.assertEQ(result[0], obj);
        Asserts.assertEQ(out[0], null);
    }

    // Test widening conversions from [Q to [L
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public static MyValue1[] test79(MyValue1[] va) {
        return va;
    }

    @Run(test = "test79")
    public void test79_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        va[0] = testValue1;
        MyValue1[] res = test79(va);
        Asserts.assertEquals(testValue1, res[0]);
        try {
            res[0] = null;
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) {
            // Expected
        }
        res[0] = testValue1;
        test79(null); // Should not throw NPE
    }

    // Same as test79 but with explicit cast and Object return
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public static Object[] test80(MyValue1[] va) {
        return (MyValue1[])va;
    }

    @Run(test = "test80")
    public void test80_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        va[0] = testValue1;
        Object[] res = test80(va);
        Asserts.assertEquals(testValue1, res[0]);
        try {
            res[0] = null;
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) {
            // Expected
        }
        res[0] = testValue1;
        test80(null); // Should not throw NPE
    }

    // Test mixing widened and boxed array type
    @Test
    public static long test81(MyValue1[] va1, MyValue1[] va2, MyValue1 vt, boolean b, boolean shouldThrow) {
        MyValue1[] result = b ? va1 : va2;
        try {
            result[0] = vt;
        } catch (NullPointerException npe) {
            // Ignored
        }
        return result[1].hash();
    }

    @Run(test = "test81")
    public void test81_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        MyValue1[] vaB = new MyValue1[2];
        va[1] = testValue1;
        vaB[1] = testValue1;
        long res = test81(va, vaB, testValue1, true, true);
        Asserts.assertEquals(testValue1, va[0]);
        Asserts.assertEquals(res, testValue1.hash());
        res = test81(va, vaB, testValue1, false, false);
        Asserts.assertEquals(testValue1, vaB[0]);
        Asserts.assertEquals(res, testValue1.hash());
        res = test81(va, va, testValue1, false, true);
        Asserts.assertEquals(testValue1, va[0]);
        Asserts.assertEquals(res, testValue1.hash());
    }

    // Same as test81 but more cases and null writes
    @Test
    public static long test82(MyValue1[] va1, MyValue1[] va2, MyValue1 vt1, MyValue1 vt2, int i, boolean shouldThrow) {
        MyValue1[] result = null;
        if (i == 0) {
            result = va1;
        } else if (i == 1) {
            result = va2;
        } else if (i == 2) {
            result = new MyValue1[2];
            result[1] = vt1;
        } else if (i == 3) {
            result = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
            result[1] = vt1;
        }
        try {
            result[0] = (i <= 1) ? null : vt2;
            if (shouldThrow) {
                throw new RuntimeException("NullPointerException expected");
            }
        } catch (NullPointerException npe) {
            Asserts.assertTrue(shouldThrow, "NullPointerException thrown");
        }
        result[0] = vt1;
        return result[1].hash();
    }

    @Run(test = "test82")
    public void test82_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        MyValue1[] vaB = new MyValue1[2];
        va[1] = testValue1;
        vaB[1] = testValue1;
        long res = test82(va, vaB, testValue1, testValue1, 0, true);
        Asserts.assertEquals(testValue1, va[0]);
        Asserts.assertEquals(res, testValue1.hash());
        res = test82(va, vaB, testValue1, testValue1, 1, false);
        Asserts.assertEquals(testValue1, vaB[0]);
        Asserts.assertEquals(res, testValue1.hash());
        res = test82(va, va, testValue1, testValue1, 1, true);
        Asserts.assertEquals(testValue1, va[0]);
        Asserts.assertEquals(res, testValue1.hash());
        res = test82(va, va, testValue1, null, 2, false);
        Asserts.assertEquals(testValue1, va[0]);
        Asserts.assertEquals(res, testValue1.hash());
        res = test82(va, va, testValue1, null, 3, true);
        Asserts.assertEquals(testValue1, va[0]);
        Asserts.assertEquals(res, testValue1.hash());
    }

    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public static long test83(MyValue1[] va) {
        MyValue1[] result = va;
        return result[0].hash();
    }

    @Run(test = "test83")
    public void test83_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 42, MyValue1.DEFAULT);
        va[0] = testValue1;
        long res = test83(va);
        Asserts.assertEquals(res, testValue1.hash());
    }

    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        failOn = {ALLOC_OF_MYVALUE_KLASS, LOOP, UNSTABLE_IF_TRAP, PREDICATE_TRAP},
        counts = {STORE_OF_ANY_KLASS, "= 38"})
    public static MyValue1[] test84(MyValue1 vt1, MyValue1 vt2) {
        MyValue1[] result = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        result[0] = vt1;
        result[1] = vt2;
        return result;
    }

    @Run(test = "test84")
    public void test84_verifier() {
        MyValue1[] res = test84(testValue1, testValue1);
        Asserts.assertEquals(testValue1, res[0]);
        Asserts.assertEquals(testValue1, res[1]);
        try {
            test84(testValue1, null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) {
            // Expected
        }
    }

    @Test
    public static long test85(MyValue1[] va, MyValue1 val) {
        va[0] = val;
        return va[1].hash();
    }

    @Run(test = "test85")
    public void test85_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        MyValue1[] vab = new MyValue1[2];
        va[1] = testValue1;
        vab[1] = testValue1;
        long res = test85(va, testValue1);
        Asserts.assertEquals(res, testValue1.hash());
        Asserts.assertEquals(testValue1, va[0]);
        res = test85(vab, testValue1);
        Asserts.assertEquals(res, testValue1.hash());
        Asserts.assertEquals(testValue1, vab[0]);
    }

    // Same as test85 but with ref value
    @Test
    public static long test86(MyValue1[] va, MyValue1 val) {
        va[0] = val;
        return va[1].hash();
    }

    @Run(test = "test86")
    public void test86_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        MyValue1[] vab = new MyValue1[2];
        va[1] = testValue1;
        vab[1] = testValue1;
        long res = test86(va, testValue1);
        Asserts.assertEquals(res, testValue1.hash());
        Asserts.assertEquals(testValue1, va[0]);
        try {
            test86(va, null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException npe) {
            // Expected
        }
        res = test86(vab, testValue1);
        Asserts.assertEquals(res, testValue1.hash());
        Asserts.assertEquals(testValue1, vab[0]);
        res = test86(vab, null);
        Asserts.assertEquals(res, testValue1.hash());
        Asserts.assertEquals(null, vab[0]);
    }

    // Test initialization of nullable array with constant
    @Test
    public long test87() {
        MyValue1[] va = new MyValue1[1];
        va[0] = testValue1;
        return va[0].hash();
    }

    @Run(test = "test87")
    public void test87_verifier() {
        long result = test87();
        Asserts.assertEQ(result, hash());
    }


    // Test casting to null restricted array
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public static MyValue1[] test88(Class c, MyValue1[] va) {
        return (MyValue1[])c.cast(va);
    }

    @Run(test = "test88")
    public void test88_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        Class c = va.getClass();
        va[0] = testValue1;
        MyValue1[] res = test88(c, va);
        Asserts.assertEquals(testValue1, res[0]);
        Asserts.assertEquals(res, va);
        res[0] = testValue1;
        test88(c, null); // Should not throw NPE
        va = new MyValue1[1];
        res = test88(c, va);
        Asserts.assertEquals(res, va);
    }

    // Same as test88 but with Object argument
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, ALLOC_ARRAY_OF_MYVALUE_KLASS, LOOP, LOAD_OF_ANY_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public static MyValue1[] test89(Class c, Object[] va) {
        return (MyValue1[])c.cast(va);
    }

    @Run(test = "test89")
    public void test89_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        Class c = va.getClass();
        va[0] = testValue1;
        MyValue1[] res = test89(c, va);
        Asserts.assertEquals(testValue1, res[0]);
        res[0] = testValue1;
        test89(c, null); // Should not throw NPE
        va = new MyValue1[1];
        res = test89(c, va);
        Asserts.assertEquals(res, va);
    }

    // More cast tests
    @Test
    public static MyValue1[] test90(Object va) {
        return (MyValue1[])va;
    }

    @Run(test = "test90")
    public void test90_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        MyValue1[] vab = new MyValue1[1];
        try {
          // Trigger some ClassCastExceptions so C2 does not add an uncommon trap
          test90(new NonValueClass[0]);
        } catch (ClassCastException cce) {
          // Expected
        }
        test90(va);
        test90(vab);
        test90(null);
    }

    @Test
    public static MyValue1[] test91(Object[] va) {
        return (MyValue1[])va;
    }

    @Run(test = "test91")
    public void test91_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        MyValue1[] vab = new MyValue1[1];
        try {
          // Trigger some ClassCastExceptions so C2 does not add an uncommon trap
          test91(new NonValueClass[0]);
        } catch (ClassCastException cce) {
          // Expected
        }
        test91(va);
        test91(vab);
        test91(null);
    }

    // Test if arraycopy intrinsic correctly checks for flattened source array
    @Test
    public static void test92(MyValue1[] src, MyValue1[] dst) {
        System.arraycopy(src, 0, dst, 0, 2);
    }

    @Run(test = "test92")
    public void test92_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        MyValue1[] vab = new MyValue1[2];
        va[0] = testValue1;
        vab[0] = testValue1;
        test92(va, vab);
        Asserts.assertEquals(va[0], vab[0]);
        Asserts.assertEquals(va[1], vab[1]);
    }

    @Test
    public static void test93(Object src, MyValue1[] dst) {
        System.arraycopy(src, 0, dst, 0, 2);
    }

    @Run(test = "test93")
    public void test93_verifier() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        MyValue1[] vab = new MyValue1[2];
        va[0] = testValue1;
        vab[0] = testValue1;
        test93(va, vab);
        Asserts.assertEquals(va[0], vab[0]);
        Asserts.assertEquals(va[1], vab[1]);
    }

    // Test non-escaping allocation with arraycopy
    // that does not modify loaded array element.
    @Test
    public static long test94() {
        MyValue1[] src = new MyValue1[8];
        MyValue1[] dst = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 8, MyValue1.DEFAULT);
        for (int i = 1; i < 8; ++i) {
            src[i] = testValue1;
        }
        System.arraycopy(src, 1, dst, 2, 6);
        return dst[0].hash();
    }

    @Run(test = "test94")
    public static void test94_verifier() {
        long result = test94();
        Asserts.assertEquals(result, MyValue1.createDefaultInline().hash());
    }

    // Test meeting constant TypeInstPtr with InlineTypeNode
    @ForceInline
    public long test95_callee() {
        MyValue1[] va = new MyValue1[1];
        va[0] = testValue1;
        return va[0].hashInterpreted();
    }

    @Test
    public long test95() {
        return test95_callee();
    }

    @Run(test = "test95")
    @Warmup(0)
    public void test95_verifier() {
        long result = test95();
        Asserts.assertEQ(result, hash());
    }

    // Matrix multiplication test to exercise type flow analysis with nullable value class arrays
    static value class Complex {
        private final double re;
        private final double im;

        Complex(double re, double im) {
            this.re = re;
            this.im = im;
        }

        public Complex add(Complex that) {
            return new Complex(this.re + that.re, this.im + that.im);
        }

        public Complex mul(Complex that) {
            return new Complex(this.re * that.re - this.im * that.im,
                               this.re * that.im + this.im * that.re);
        }
    }

    @Test
    public Complex[][] test96(Complex[][] A, Complex[][] B) {
        int size = A.length;
        Complex[][] R = new Complex[size][size];
        for (int i = 0; i < size; i++) {
            for (int k = 0; k < size; k++) {
                Complex aik = A[i][k];
                for (int j = 0; j < size; j++) {
                    R[i][j] = B[i][j].add(aik.mul((Complex)B[k][j]));
                }
            }
        }
        return R;
    }

    static Complex[][] test96_A = new Complex[10][10];
    static Complex[][] test96_B = new Complex[10][10];
    static Complex[][] test96_R;

    static {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                test96_A[i][j] = new Complex(rI, rI);
                test96_B[i][j] = new Complex(rI, rI);
            }
        }
    }

    @Run(test = "test96")
    public void test96_verifier() {
        Complex[][] result = test96(test96_A, test96_B);
        if (test96_R == null) {
            test96_R = result;
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                Asserts.assertEQ(result[i][j], test96_R[i][j]);
            }
        }
    }

    // Test loads from vararg arrays
    @Test
    @IR(failOn = {LOAD_UNKNOWN_INLINE})
    public static Object test97(Object... args) {
        return args[0];
    }

    @Run(test = "test97")
    public static void test97_verifier() {
        Object obj = new Object();
        Object result = test97(obj);
        Asserts.assertEquals(result, obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        myInt[0] = otherObj;
        result = test97((Object[])myInt);
        Asserts.assertEquals(result, otherObj);
    }

    @Test
    public static Object test98(Object... args) {
        return args[0];
    }

    @Run(test = "test98")
    public static void test98_verifier(RunInfo info) {
        Object obj = new Object();
        Object result = test98(obj);
        Asserts.assertEquals(result, obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        myInt[0] = otherObj;
        result = test98((Object[])myInt);
        Asserts.assertEquals(result, otherObj);
        if (!info.isWarmUp()) {
            MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
            MyValue1[] vab = new MyValue1[1];
            result = test98((Object[])va);
            Asserts.assertEquals(MyValue1.createDefaultInline(), result);
            result = test98((Object[])vab);
            Asserts.assertEquals(null, result);
        }
    }

    @Test
    public static Object test99(Object... args) {
        return args[0];
    }

    @Run(test = "test99")
    public static void test99_verifier(RunInfo info) {
        Object obj = new Object();
        Object result = test99(obj);
        Asserts.assertEquals(result, obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        myInt[0] = otherObj;
        result = test99((Object[])myInt);
        Asserts.assertEquals(result, otherObj);
        if (!info.isWarmUp()) {
            try {
                test99((Object[])null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }
        }
    }

    @Test
    public static Object test100(Object... args) {
        return args[0];
    }

    @Run(test = "test100")
    public static void test100_verifier(RunInfo info) {
        Object obj = new Object();
        Object result = test100(obj);
        Asserts.assertEquals(result, obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        myInt[0] = otherObj;
        result = test100((Object[])myInt);
        Asserts.assertEquals(result, otherObj);
        if (!info.isWarmUp()) {
            try {
                test100();
                throw new RuntimeException("No AIOOBE thrown");
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                // Expected
            }
        }
    }

    // Test stores to varag arrays
    @Test
    @IR(failOn = STORE_UNKNOWN_INLINE)
    public static void test101(Object val, Object... args) {
        args[0] = val;
    }

    @Run(test = "test101")
    public static void test101_verifier() {
        Object obj = new Object();
        test101(obj, obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        test101(otherObj, (Object[])myInt);
        Asserts.assertEquals(myInt[0], otherObj);
        test101(null, (Object[])myInt);
        Asserts.assertEquals(myInt[0], null);
    }

    @Test
    public static void test102(Object val, Object... args) {
        args[0] = val;
    }

    @Run(test = "test102")
    public static void test102_verifier(RunInfo info) {
        Object obj = new Object();
        test102(obj, obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        test102(otherObj, (Object[])myInt);
        Asserts.assertEquals(myInt[0], otherObj);
        test102(null, (Object[])myInt);
        Asserts.assertEquals(myInt[0], null);
        if (!info.isWarmUp()) {
            MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
            MyValue1[] vab = new MyValue1[1];
            test102(testValue1, (Object[])va);
            Asserts.assertEquals(testValue1, va[0]);
            test102(testValue1, (Object[])vab);
            Asserts.assertEquals(testValue1, vab[0]);
            test102(null, (Object[])vab);
            Asserts.assertEquals(null, vab[0]);
        }
    }

    @Test
    public static void test103(Object val, Object... args) {
        args[0] = val;
    }

    @Run(test = "test103")
    public static void test103_verifier(RunInfo info) {
        Object obj = new Object();
        test103(obj, obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        test103(otherObj, (Object[])myInt);
        Asserts.assertEquals(myInt[0], otherObj);
        test103(null, (Object[])myInt);
        Asserts.assertEquals(myInt[0], null);
        if (!info.isWarmUp()) {
            MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
            try {
                test103(null, (Object[])va);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }
        }
    }

    @Test
    public static void test104(Object val, Object... args) {
        args[0] = val;
    }

    @Run(test = "test104")
    public static void test104_verifier(RunInfo info) {
        Object obj = new Object();
        test104(obj, obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        test104(otherObj, (Object[])myInt);
        Asserts.assertEquals(myInt[0], otherObj);
        test104(null, (Object[])myInt);
        Asserts.assertEquals(myInt[0], null);
        if (!info.isWarmUp()) {
            try {
                test104(testValue1);
                throw new RuntimeException("No AIOOBE thrown");
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                // Expected
            }
        }
    }

    @Test
    public static void test105(Object val, Object... args) {
        args[0] = val;
    }

    @Run(test = "test105")
    public static void test105_verifier(RunInfo info) {
        Object obj = new Object();
        test105(obj, obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        test105(otherObj, (Object[])myInt);
        Asserts.assertEquals(myInt[0], otherObj);
        test105(null, (Object[])myInt);
        Asserts.assertEquals(myInt[0], null);
        if (!info.isWarmUp()) {
            try {
                test105(testValue1, (Object[])null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException npe) {
                // Expected
            }
        }
    }

    @Test
    public static Object[] test106(Object[] dst, Object... args) {
        // Access array to speculate on non-flatness
        if (args[0] == null) {
            args[0] = testValue1;
        }
        System.arraycopy(args, 0, dst, 0, args.length);
        System.arraycopy(dst, 0, args, 0, dst.length);
        Object[] clone = args.clone();
        if (clone[0] == null) {
            throw new RuntimeException("Unexpected null");
        }
        return Arrays.copyOf(args, args.length, Object[].class);
    }

    @Run(test = "test106")
    public static void test106_verifier(RunInfo info) {
        Object[] dst = new Object[1];
        Object obj = new Object();
        Object[] result = test106(dst, obj);
        Asserts.assertEquals(result[0], obj);
        NonValueClass[] myInt = new NonValueClass[1];
        NonValueClass otherObj = new NonValueClass(rI);
        myInt[0] = otherObj;
        result = test106(myInt, (Object[])myInt);
        Asserts.assertEquals(result[0], otherObj);
        if (!info.isWarmUp()) {
            MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
            MyValue1[] vab = new MyValue1[1];
            result = test106(va, (Object[])va);
            Asserts.assertEquals(MyValue1.createDefaultInline(), result[0]);
            result = test106(vab, (Object[])vab);
            Asserts.assertEquals(testValue1, result[0]);
        }
    }

    // Test that allocation is not replaced by non-dominating allocation
    @ForceInline
    public long test107_helper(MyValue1[] va, MyValue1 vt) {
        try {
            va[0] = vt;
        } catch (NullPointerException npe) { }
        return va[1].hash();
    }

    @Test
    public void test107() {
        MyValue1[] va = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 2, MyValue1.DEFAULT);
        MyValue1[] tmp = new MyValue1[2];
        long res1 = test107_helper(va, testValue1);
        long res2 = test107_helper(va, testValue1);
        Asserts.assertEquals(testValue1, va[0]);
        Asserts.assertEquals(res1, MyValue1.createDefaultInline().hash());
        Asserts.assertEquals(res2, MyValue1.createDefaultInline().hash());
    }

    @Run(test = "test107")
    public void test107_verifier() {
        test107();
    }

    @Test
    public Object test108(MyValue1[] src, boolean flag) {
        MyValue1[] dst = new MyValue1[8];
        System.arraycopy(src, 1, dst, 2, 6);
        if (flag) {} // uncommon trap
        return dst[2];
    }

    @Run(test = "test108")
    @Warmup(10000)
    public void test108_verifier(RunInfo info) {
        MyValue1[] src = new MyValue1[8];
        test108(src, !info.isWarmUp());
    }

    // Test LoadNode::can_see_arraycopy_value optimization
    @Test
    public static void test109() {
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        MyValue1[] dst = new MyValue1[1];
        src[0] = testValue1;
        System.arraycopy(src, 0, dst, 0, 1);
        Asserts.assertEquals(src[0], dst[0]);
    }

    @Run(test = "test109")
    public void test109_verifier() {
        test109();
    }

    // Same as test109 but with Object destination array
    @Test
    public static void test110() {
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        Object[] dst = new Object[1];
        src[0] = testValue1;
        System.arraycopy(src, 0, dst, 0, 1);
        Asserts.assertEquals(src[0], dst[0]);
    }

    @Run(test = "test110")
    public void test110_verifier() {
        test110();
    }

    // Same as test109 but with Arrays.copyOf
    @Test
    public static void test111() {
        MyValue1[] src = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
        src[0] = testValue1;
        MyValue1[] dst = Arrays.copyOf(src, src.length, MyValue1[].class);
        Asserts.assertEquals(src[0], dst[0]);
    }

    @Run(test = "test111")
    public void test111_verifier() {
        test111();
    }

    static final MyValue1[] refArray = new MyValue1[2];
    static final MyValue1[] flatArray = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);

    // Test scalarization
    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public int test112(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = refArray[0];
        }
        return val.x;
    }

    @Run(test = "test112")
    public void test112_verifier(RunInfo info) {
        refArray[0] = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test112(true), refArray[0].x);
        Asserts.assertEquals(test112(false), testValue1.x);
        if (!info.isWarmUp()) {
            refArray[0] = null;
            try {
                Asserts.assertEquals(test112(false), testValue1.x);
                test112(true);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Same as test112 but with call to hash()
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS, UNSTABLE_IF_TRAP, PREDICATE_TRAP})
    public long test113(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = refArray[0];
        }
        return val.hash();
    }

    @Run(test = "test113")
    public void test113_verifier(RunInfo info) {
        refArray[0] = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test113(true), refArray[0].hash());
        Asserts.assertEquals(test113(false), testValue1.hash());
        if (!info.isWarmUp()) {
            refArray[0] = null;
            try {
                Asserts.assertEquals(test113(false), testValue1.hash());
                test113(true);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    @Test
    public MyValue1 test114(boolean b) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b) {
            val = refArray[0];
        }
        return val;
    }

    @Run(test = "test114")
    public void test114_verifier(RunInfo info) {
        refArray[0] = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(refArray[0], test114(true));
        Asserts.assertEquals(testValue1, test114(false));
        if (!info.isWarmUp()) {
            refArray[0] = null;
            Asserts.assertEquals(test114(true), null);
        }
    }

    // Test scalarization when .ref is referenced in safepoint debug info
    @Test
    @IR(failOn = {ALLOC_OF_MYVALUE_KLASS, STORE_OF_ANY_KLASS})
    public int test115(boolean b1, boolean b2, Method m) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = refArray[0];
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return val.x;
    }

    @Run(test = "test115")
    public void test115_verifier(RunInfo info) {
        refArray[0] = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(test115(true, false, info.getTest()), refArray[0].x);
        Asserts.assertEquals(test115(false, false, info.getTest()), testValue1.x);
        if (!info.isWarmUp()) {
            refArray[0] = null;
            try {
                Asserts.assertEquals(test115(false, false, info.getTest()), testValue1.x);
                test115(true, false, info.getTest());
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException e) {
                // Expected
            }
            refArray[0] = MyValue1.createWithFieldsInline(rI+1, rL+1);
            Asserts.assertEquals(test115(true, true, info.getTest()), refArray[0].x);
            Asserts.assertEquals(test115(false, true, info.getTest()), testValue1.x);
        }
    }

    @Test
    public MyValue1 test116(boolean b1, boolean b2, Method m) {
        MyValue1 val = MyValue1.createWithFieldsInline(rI, rL);
        if (b1) {
            val = refArray[0];
        }
        if (b2) {
            // Uncommon trap
            TestFramework.deoptimize(m);
        }
        return val;
    }

    @Run(test = "test116")
    public void test116_verifier(RunInfo info) {
        refArray[0] = MyValue1.createWithFieldsInline(rI+1, rL+1);
        Asserts.assertEquals(refArray[0], test116(true, false, info.getTest()));
        Asserts.assertEquals(testValue1, test116(false, false, info.getTest()));
        if (!info.isWarmUp()) {
            refArray[0] = null;
            Asserts.assertEquals(test116(true, false, info.getTest()), null);
            refArray[0] = MyValue1.createWithFieldsInline(rI+1, rL+1);
            Asserts.assertEquals(refArray[0], test116(true, true, info.getTest()));
            Asserts.assertEquals(testValue1, test116(false, true, info.getTest()));
        }
    }

    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS})
    public int test117(boolean b) {
        MyValue1 val = null;
        if (b) {
            val = refArray[0];
        }
        return val.x;
    }

    @Run(test = "test117")
    public void test117_verifier() {
        refArray[0] = testValue1;
        Asserts.assertEquals(test117(true), testValue1.x);
        try {
            test117(false);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public MyValue1 test118(boolean b) {
        MyValue1 val = null;
        if (b) {
            val = refArray[0];
        }
        return val;
    }

    @Run(test = "test118")
    public void test118_verifier() {
        refArray[0] = testValue1;
        Asserts.assertEquals(testValue1, test118(true));
        Asserts.assertEquals(null, test118(false));
    }

    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS})
    public int test119(boolean b) {
        MyValue1 val = refArray[0];
        if (b) {
            val = null;
        }
        return val.x;
    }

    @Run(test = "test119")
    public void test119_verifier() {
        refArray[0] = testValue1;
        Asserts.assertEquals(test119(false), testValue1.x);
        try {
            test119(true);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public MyValue1 test120(boolean b) {
        MyValue1 val = refArray[0];
        if (b) {
            val = null;
        }
        return val;
    }

    @Run(test = "test120")
    public void test120_verifier() {
        refArray[0] = testValue1;
        Asserts.assertEquals(testValue1, test120(false));
        Asserts.assertEquals(null, test120(true));
    }

    @ForceInline
    public Object test121_helper() {
        return flatArray[0];
    }

    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        failOn = {ALLOC},
        counts = {STORE_OF_ANY_KLASS, "= 19"})
    public void test121(boolean b) {
        Object o = null;
        if (b) {
            o = refArray[0];
        } else {
            o = test121_helper();
        }
        if (o == null) {
            return;
        }
        flatArray[0] = (MyValue1)o;
    }

    @Run(test = "test121")
    public void test121_verifier() {
        refArray[0] = testValue1;
        MyValue1 vt = MyValue1.createWithFieldsInline(rI+1, rL+1);
        flatArray[0] = vt;
        test121(false);
        Asserts.assertEquals(vt, flatArray[0]);
        test121(true);
        Asserts.assertEquals(testValue1, flatArray[0]);
    }

    @ForceInline
    public Object test122_helper() {
        return refArray[0];
    }

    @Test
    @IR(applyIf = {"UseArrayFlattening", "true"},
        failOn = {ALLOC},
        counts = {STORE_OF_ANY_KLASS, "= 19"})
    public void test122(boolean b) {
        Object o = null;
        if (b) {
            o = flatArray[0];
        } else {
            o = test122_helper();
        }
        if (o == null) {
            return;
        }
        flatArray[0] = (MyValue1)o;
    }

    @Run(test = "test122")
    public void test122_verifier() {
        refArray[0] = testValue1;
        test122(false);
        Asserts.assertEquals(testValue1, flatArray[0]);
        MyValue1 vt = MyValue1.createWithFieldsInline(rI+1, rL+1);
        flatArray[0] = vt;
        test122(true);
        Asserts.assertEquals(vt, flatArray[0]);
    }

    @ForceInline
    public Object test123_helper() {
        return refArray[0];
    }

    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS})
    public long test123(boolean b, MyValue1 val, Method m, boolean deopt) {
        MyValue1[] array = new MyValue1[1];
        array[0] = val;
        Object res = null;
        if (b) {
            res = array[0];
        } else {
            res = test123_helper();
        }
        if (deopt) {
            // uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue1)res).hash();
    }

    @Run(test = "test123")
    public void test123_verifier(RunInfo info) {
        refArray[0] = MyValue1.createDefaultInline();
        Asserts.assertEquals(test123(true, testValue1, info.getTest(), false), testValue1.hash());
        Asserts.assertEquals(test123(false, testValue1, info.getTest(), false), MyValue1.createDefaultInline().hash());
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test123(true, testValue1, info.getTest(), true), testValue1.hash());
        }
    }

    @ForceInline
    public Object test124_helper(MyValue2 val) {
        MyValue2[] array = new MyValue2[1];
        array[0] = val;
        return array[0];
    }

    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS})
    public long test124(boolean b, MyValue2 val, Method m, boolean deopt) {
        Object res = null;
        if (b) {
            res = MyValue2.createWithFieldsInline(rI+1, rD+1);
        } else {
            res = test124_helper(val);
        }
        if (deopt) {
            // uncommon trap
            TestFramework.deoptimize(m);
        }
        return ((MyValue2)res).hash();
    }

    @Run(test = "test124")
    public void test124_verifier(RunInfo info) {
        refArray[0] = MyValue1.createDefaultInline();
        MyValue2 val1 = MyValue2.createWithFieldsInline(rI, rD);
        MyValue2 val2 = MyValue2.createWithFieldsInline(rI+1, rD+1);
        Asserts.assertEquals(test124(true, val1, info.getTest(), false), val2.hash());
        Asserts.assertEquals(test124(false, val1, info.getTest(), false), val1.hash());
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test124(true, val1, info.getTest(), true), val2.hash());
        }
    }

    @ForceInline
    public void test125_helper(Object[] array, MyValue2 val) {
        array[0] = val;
    }

    @Test
    @IR(failOn = {ALLOC, STORE_OF_ANY_KLASS})
    public long test125(boolean b, MyValue2 val, Method m, boolean deopt) {
        Object[] res = new MyValue2[1];
        if (b) {
            res[0] = MyValue2.createWithFieldsInline(rI+1, rD+1);
        } else {
            test125_helper(res, val);
        }
        val = ((MyValue2)res[0]);
        if (deopt) {
            // uncommon trap
            TestFramework.deoptimize(m);
        }
        return val.hash();
    }

    @Run(test = "test125")
    public void test125_verifier(RunInfo info) {
        refArray[0] = MyValue1.createDefaultInline();
        MyValue2 val1 = MyValue2.createWithFieldsInline(rI, rD);
        MyValue2 val2 = MyValue2.createWithFieldsInline(rI+1, rD+1);
        Asserts.assertEquals(test125(true, val1, info.getTest(), false), val2.hash());
        Asserts.assertEquals(test125(false, val1, info.getTest(), false), val1.hash());
        if (!info.isWarmUp()) {
            Asserts.assertEquals(test125(true, val1, info.getTest(), true), val2.hash());
        }
    }

    static Object oFld = null;

    static value class MyValue126  {
        int x;

        MyValue126(int x) {
            this.x = x;
        }
    }

    // Test that result of access to unknown flat array is not marked as null-free
    @Test
    public void test126(Object[] array, int i) {
        oFld = array[i];
    }

    @Run(test = "test126")
    @Warmup(0)
    public void test126_verifier() {
        MyValue126[] array = (MyValue126[]) ValueClass.newNullableAtomicArray(MyValue126.class, 2);
        array[1] = new MyValue126(rI);
        test126(array, 1);
        Asserts.assertEquals(oFld, new MyValue126(rI));
        test126(array, 0);
        Asserts.assertEquals(oFld, null);
    }

    // Same as test126 but different failure mode
    @Test
    public void test127(Object[] array, int i) {
        oFld = (MyValue126)array[i];
    }

    @Run(test = "test127")
    @Warmup(0)
    public void test127_verifier() {
        MyValue126[] array = (MyValue126[]) ValueClass.newNullableAtomicArray(MyValue126.class, 2);
        array[1] = new MyValue126(rI);
        test127(array, 1);
        Asserts.assertEquals(oFld, new MyValue126(rI));
        test127(array, 0);
        Asserts.assertEquals(oFld, null);
    }
}
