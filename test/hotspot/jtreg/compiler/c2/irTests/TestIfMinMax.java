/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8324655 8329797 8331090
 * @key randomness
 * @summary Test that if expressions are properly folded into min/max nodes
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIfMinMax
 */
public class TestIfMinMax {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MIN_I, "1" })
    public int testMinI1(int a, int b) {
        return a < b ? a : b;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MIN_I, "1" })
    public int testMinI2(int a, int b) {
        return a > b ? b : a;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MAX_I, "1" })
    public int testMaxI1(int a, int b) {
        return a > b ? a : b;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MAX_I, "1" })
    public int testMaxI2(int a, int b) {
        return a < b ? b : a;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MIN_I, "1" })
    public int testMinI1E(int a, int b) {
        return a <= b ? a : b;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MIN_I, "1" })
    public int testMinI2E(int a, int b) {
        return a >= b ? b : a;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MAX_I, "1" })
    public int testMaxI1E(int a, int b) {
        return a >= b ? a : b;
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MAX_I, "1" })
    public int testMaxI2E(int a, int b) {
        return a <= b ? b : a;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MIN_L, "1" })
    public long testMinL1(long a, long b) {
        return a < b ? a : b;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MIN_L, "1" })
    public long testMinL2(long a, long b) {
        return a > b ? b : a;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MAX_L, "1" })
    public long testMaxL1(long a, long b) {
        return a > b ? a : b;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MAX_L, "1" })
    public long testMaxL2(long a, long b) {
        return a < b ? b : a;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MIN_L, "1" })
    public long testMinL1E(long a, long b) {
        return a <= b ? a : b;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MIN_L, "1" })
    public long testMinL2E(long a, long b) {
        return a >= b ? b : a;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MAX_L, "1" })
    public long testMaxL1E(long a, long b) {
        return a >= b ? a : b;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MAX_L, "1" })
    public long testMaxL2E(long a, long b) {
        return a <= b ? b : a;
    }

    public class Dummy {
        long l;
        public Dummy(long l) { this.l = l; }
    }

    @Setup
    Object[] setupDummyArray() {
        Dummy[] arr = new Dummy[512];
        for (int i = 0; i < 512; i++) {
            arr[i] = new Dummy(RANDOM.nextLong());
        }
        return new Object[] { arr };
    }

    @Test
    @Arguments(setup = "setupDummyArray")
    @IR(failOn = { IRNode.MAX_L })
    public long testMaxLAndBarrierInLoop(Dummy[] arr) {
        long result = 0;
        for (int i = 0; i < arr.length; ++i) {
            result += Math.max(arr[i].l, 1);
        }
        return result;
    }

    @Test
    @Arguments(setup = "setupDummyArray")
    @IR(failOn = { IRNode.MIN_L })
    public long testMinLAndBarrierInLoop(Dummy[] arr) {
        long result = 0;
        for (int i = 0; i < arr.length; ++i) {
            result += Math.min(arr[i].l, 1);
        }
        return result;
    }

    @Setup
    static Object[] setupIntArrays() {
        int[] a = new int[512];
        int[] b = new int[512];

        // Fill from 1 to 125
        for (int i = 0; i < 125; i++) {
            a[i] = i + 1;
            b[i] = 1;
        }

        // Fill from -1 to -125
        for (int i = 125; i < 250; i++) {
            a[i] = -(i - 124);
            b[i] = 1;
        }

        for (int i = 250; i < 512; i++) {
            a[i] = RANDOM.nextInt();
            b[i] = 1;
        }

        return new Object[] { a, b };
    }

    @Setup
    static Object[] setupLongArrays() {
        long[] a = new long[512];
        long[] b = new long[512];

        // Fill from 1 to 125
        for (int i = 0; i < 125; i++) {
            a[i] = i + 1;
            b[i] = 1;
        }

        // Fill from -1 to -125
        for (int i = 125; i < 250; i++) {
            a[i] = -(i - 124);
            b[i] = 1;
        }

        for (int i = 250; i < 512; i++) {
            a[i] = RANDOM.nextLong();
            b[i] = 1;
        }

        return new Object[] { a, b };
    }

    @Test
    @IR(applyIf = { "SuperWordReductions", "true" },
        applyIfCPUFeatureOr = { "sse4.1", "true" , "asimd" , "true", "rvv", "true"},
        counts = { IRNode.MAX_REDUCTION_V, "> 0" })
    @Arguments(setup = "setupIntArrays")
    public Object[] testMaxIntReduction(int[] a, int[] b) {
        int r = 0;
        for (int i = 0; i < a.length; i++) {
            int aI = a[i] * b[i];

            r = aI > r ? aI : r;
        }

        return new Object[] { a, b, r };
    }

    @Check(test = "testMaxIntReduction")
    public void checkTestMaxIntReduction(Object[] vals) {
        int[] a = (int[]) vals[0];
        int[] b = (int[]) vals[1];
        int testRet = (int) vals[2];

        int r = 0;
        for (int i = 0; i < a.length; i++) {
            int aI = a[i] * b[i];

            r = aI > r ? aI : r;
        }

        if (r != testRet) {
            throw new IllegalStateException("Int max reduction test failed: expected " + testRet + " but got " + r);
        }
    }

    @Test
    @IR(applyIf = { "SuperWordReductions", "true" },
        applyIfCPUFeatureOr = { "sse4.1", "true" , "asimd" , "true", "rvv", "true"},
        counts = { IRNode.MIN_REDUCTION_V, "> 0" })
    @Arguments(setup = "setupIntArrays")
    public Object[] testMinIntReduction(int[] a, int[] b) {
        int r = 0;

        for (int i = 0; i < a.length; i++) {
            int aI = a[i] * b[i];

            r = aI < r ? aI : r;
        }

        return new Object[] { a, b, r };
    }

    @Check(test = "testMinIntReduction")
    public void checkTestMinIntReduction(Object[] vals) {
        int[] a = (int[]) vals[0];
        int[] b = (int[]) vals[1];
        int testRet = (int) vals[2];

        int r = 0;
        for (int i = 0; i < a.length; i++) {
            int aI = a[i] * b[i];

            r = aI < r ? aI : r;
        }

        if (r != testRet) {
            throw new IllegalStateException("Int min reduction test failed: expected " + testRet + " but got " + r);
        }
    }

    @Test
    @IR(applyIf = { "SuperWordReductions", "true" },
        applyIfCPUFeature = { "avx512", "true" },
        counts = { IRNode.MAX_REDUCTION_V, "> 0" })
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfAnd = { "SuperWordReductions", "true", "MaxVectorSize", ">=32" },
        applyIfCPUFeature = { "rvv", "true" },
        counts = { IRNode.MAX_REDUCTION_V, "> 0" })
    @Arguments(setup = "setupLongArrays")
    public Object[] testMaxLongReduction(long[] a, long[] b) {
        long r = 0;

        for (int i = 0; i < a.length; i++) {
            long aI = a[i] * b[i];

            r = aI > r ? aI : r;
        }

        return new Object[] { a, b, r };
    }

    @Check(test = "testMaxLongReduction")
    public void checkTestMaxLongReduction(Object[] vals) {
        long[] a = (long[]) vals[0];
        long[] b = (long[]) vals[1];
        long testRet = (long) vals[2];

        long r = 0;
        for (int i = 0; i < a.length; i++) {
            long aI = a[i] * b[i];

            r = aI > r ? aI : r;
        }

        if (r != testRet) {
            throw new IllegalStateException("Long max reduction test failed: expected " + testRet + " but got " + r);
        }
    }

    @Test
    @IR(applyIf = { "SuperWordReductions", "true" },
        applyIfCPUFeature = { "avx512", "true" },
        counts = { IRNode.MIN_REDUCTION_V, "> 0" })
    @IR(applyIfPlatform = {"riscv64", "true"},
        applyIfAnd = { "SuperWordReductions", "true", "MaxVectorSize", ">=32" },
        applyIfCPUFeature = { "rvv", "true" },
        counts = { IRNode.MIN_REDUCTION_V, "> 0" })
    @Arguments(setup = "setupLongArrays")
    public Object[] testMinLongReduction(long[] a, long[] b) {
        long r = 0;

        for (int i = 0; i < a.length; i++) {
            long aI = a[i] * b[i];

            r = aI < r ? aI : r;
        }

        return new Object[] { a, b, r };
    }

    @Check(test = "testMinLongReduction")
    public void checkTestMinLongReduction(Object[] vals) {
        long[] a = (long[]) vals[0];
        long[] b = (long[]) vals[1];
        long testRet = (long) vals[2];

        long r = 0;
        for (int i = 0; i < a.length; i++) {
            long aI = a[i] * b[i];

            r = aI < r ? aI : r;
        }

        if (r != testRet) {
            throw new IllegalStateException("Long min reduction test failed: expected " + testRet + " but got " + r);
        }
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "sse4.1", "true" , "asimd" , "true", "rvv", "true"},
        counts = { IRNode.MAX_VI, "> 0" })
    @Arguments(setup = "setupIntArrays")
    public Object[] testMaxIntVector(int[] a, int[] b) {
        int[] r = new int[a.length];

        for (int i = 0; i < a.length; i++) {
            int aI = a[i];
            int bI = b[i];

            r[i] = aI > bI ? aI : bI;
        }

        return new Object[] { a, b, r };
    }

    @Check(test = "testMaxIntVector")
    public void checkTestMaxIntVector(Object[] vals) {
        int[] a = (int[]) vals[0];
        int[] b = (int[]) vals[1];
        int[] testRet = (int[]) vals[2];

        for (int i = 0; i < a.length; i++) {
            int aI = a[i];
            int bI = b[i];

            int r = aI > bI ? aI : bI;

            if (r != testRet[i]) {
                throw new IllegalStateException("Int max vectorization test failed: expected " + testRet + " but got " + r);
            }
        }
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "sse4.1", "true" , "asimd" , "true", "rvv", "true"},
        counts = { IRNode.MIN_VI, "> 0" })
    @Arguments(setup = "setupIntArrays")
    public Object[] testMinIntVector(int[] a, int[] b) {
        int[] r = new int[a.length];

        for (int i = 0; i < a.length; i++) {
            int aI = a[i];
            int bI = b[i];

            r[i] = aI < bI ? aI : bI;
        }

        return new Object[] { a, b, r };
    }

    @Check(test = "testMinIntVector")
    public void checkTestMinIntVector(Object[] vals) {
        int[] a = (int[]) vals[0];
        int[] b = (int[]) vals[1];
        int[] testRet = (int[]) vals[2];

        for (int i = 0; i < a.length; i++) {
            int aI = a[i];
            int bI = b[i];

            int r = aI < bI ? aI : bI;

            if (r != testRet[i]) {
                throw new IllegalStateException("Int min vectorization test failed: expected " + testRet + " but got " + r);
            }
        }
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "sse4.1", "true" , "asimd" , "true", "rvv", "true"},
        counts = { IRNode.MAX_VL, "> 0" })
    @Arguments(setup = "setupLongArrays")
    public Object[] testMaxLongVector(long[] a, long[] b) {
        long[] r = new long[a.length];

        for (int i = 0; i < a.length; i++) {
            long aI = a[i];
            long bI = b[i];

            r[i] = aI > bI ? aI : bI;
        }

        return new Object[] { a, b, r };
    }

    @Check(test = "testMaxLongVector")
    public void checkTestMaxLongVector(Object[] vals) {
        long[] a = (long[]) vals[0];
        long[] b = (long[]) vals[1];
        long[] testRet = (long[]) vals[2];

        for (int i = 0; i < a.length; i++) {
            long aI = a[i];
            long bI = b[i];

            long r = aI > bI ? aI : bI;

            if (r != testRet[i]) {
                throw new IllegalStateException("Long max vectorization test failed: expected " + testRet + " but got " + r);
            }
        }
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "sse4.1", "true" , "asimd" , "true", "rvv", "true"},
        counts = { IRNode.MIN_VL, "> 0" })
    @Arguments(setup = "setupLongArrays")
    public Object[] testMinLongVector(long[] a, long[] b) {
        long[] r = new long[a.length];

        for (int i = 0; i < a.length; i++) {
            long aI = a[i];
            long bI = b[i];

            r[i] = aI < bI ? aI : bI;
        }

        return new Object[] { a, b, r };
    }

    @Check(test = "testMinLongVector")
    public void checkTestMinLongVector(Object[] vals) {
        long[] a = (long[]) vals[0];
        long[] b = (long[]) vals[1];
        long[] testRet = (long[]) vals[2];

        for (int i = 0; i < a.length; i++) {
            long aI = a[i];
            long bI = b[i];

            long r = aI < bI ? aI : bI;

            if (r != testRet[i]) {
                throw new IllegalStateException("Long min vectorization test failed: expected " + testRet + " but got " + r);
            }
        }
    }

    @Test
    @IR(failOn = { IRNode.IF }, counts = { IRNode.MIN_I, "1" })
    public int testMinIConst(int a) {
        if (a > 65535) {
            a = 65535;
        }

        return a;
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, failOn = { IRNode.IF }, counts = { IRNode.MIN_L, "1" })
    public long testMinLConst(long a) {
        if (a > 65535) {
            a = 65535;
        }

        return a;
    }

    @Run(test = { "testMinI1", "testMinI2", "testMaxI1", "testMaxI2", "testMinI1E", "testMinI2E", "testMaxI1E", "testMaxI2E", "testMinIConst" })
    public void runTestIntegers() {
        testIntegers(10, 20);
        testIntegers(20, 10);
        testIntegers(10, 10);
        testIntegers(Integer.MAX_VALUE, Integer.MIN_VALUE);
        testIntegers(Integer.MIN_VALUE, Integer.MAX_VALUE);
        testIntegers(RANDOM.nextInt(), RANDOM.nextInt());
    }

    @DontCompile
    public void testIntegers(int a, int b) {
        Asserts.assertEQ(a < b ? a : b, testMinI1(a, b));
        Asserts.assertEQ(a > b ? b : a, testMinI2(a, b));
        Asserts.assertEQ(a > b ? a : b, testMaxI1(a, b));
        Asserts.assertEQ(a < b ? b : a, testMaxI2(a, b));

        Asserts.assertEQ(a <= b ? a : b, testMinI1E(a, b));
        Asserts.assertEQ(a >= b ? b : a, testMinI2E(a, b));
        Asserts.assertEQ(a >= b ? a : b, testMaxI1E(a, b));
        Asserts.assertEQ(a <= b ? b : a, testMaxI2E(a, b));

        Asserts.assertEQ(a > 65535 ? 65535 : a, testMinIConst(a));
        Asserts.assertEQ(b > 65535 ? 65535 : b, testMinIConst(b));
    }

    @Run(test = { "testMinL1", "testMinL2", "testMaxL1", "testMaxL2", "testMinL1E", "testMinL2E", "testMaxL1E", "testMaxL2E", "testMinLConst" })
    public void runTestLongs() {
        testLongs(10, 20);
        testLongs(20, 10);
        testLongs(10, 10);
        testLongs(Integer.MAX_VALUE, Integer.MIN_VALUE);
        testLongs(Integer.MIN_VALUE, Integer.MAX_VALUE);
        testLongs(Long.MAX_VALUE, Long.MIN_VALUE);
        testLongs(Long.MIN_VALUE, Long.MAX_VALUE);
        testLongs(RANDOM.nextLong(), RANDOM.nextLong());
    }

    @DontCompile
    public void testLongs(long a, long b) {
        Asserts.assertEQ(a < b ? a : b, testMinL1(a, b));
        Asserts.assertEQ(a > b ? b : a, testMinL2(a, b));
        Asserts.assertEQ(a > b ? a : b, testMaxL1(a, b));
        Asserts.assertEQ(a < b ? b : a, testMaxL2(a, b));

        Asserts.assertEQ(a <= b ? a : b, testMinL1E(a, b));
        Asserts.assertEQ(a >= b ? b : a, testMinL2E(a, b));
        Asserts.assertEQ(a >= b ? a : b, testMaxL1E(a, b));
        Asserts.assertEQ(a <= b ? b : a, testMaxL2E(a, b));

        Asserts.assertEQ(a > 65535L ? 65535L : a, testMinLConst(a));
        Asserts.assertEQ(b > 65535L ? 65535L : b, testMinLConst(b));
    }
}
