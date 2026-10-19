/*
 * Copyright (c) 2021, 2026, Red Hat, Inc. All rights reserved.
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

import compiler.lib.ir_framework.*;
import static compiler.lib.generators.Generators.G;
import jdk.test.lib.Asserts;

import java.util.Objects;

/*
 * @test
 * @bug 8277850 8278949 8285793 8346664 8380475
 * @key randomness
 * @summary C2: optimize mask checks in counted loops
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestShiftAndMask
 */

public class TestShiftAndMask {
    public static void main(String[] args) {
        TestFramework.run();
    }

    // any X << INT_MASK_WIDTH is zero under any INT_MASK
    static final int INT_MASK_WIDTH = G.ints().restricted(1, 30).next();
    static final int INT_MAX_MASK = (1 << INT_MASK_WIDTH) - 1;
    static final int INT_MASK = G.ints().restricted(1, INT_MAX_MASK).next();
    static final int INT_MASK2 = G.ints().restricted(1, INT_MAX_MASK).next();
    static final int INT_ZERO_CONST = G.ints().next() << INT_MASK_WIDTH;

    static final int INT_RANDOM_CONST = G.ints().next();
    static final int INT_RANDOM_SHIFT = G.ints().next();
    static final int INT_RANDOM_MASK = G.ints().next();

    // any X << LONG_MASK_WIDTH is zero under any LONG_MASK
    static final int LONG_MASK_WIDTH = G.ints().restricted(1, 62).next();
    static final long LONG_MAX_MASK = (1L << LONG_MASK_WIDTH) - 1;
    static final long LONG_MASK = G.longs().restricted(1L, LONG_MAX_MASK).next();
    static final long LONG_MASK2 = G.longs().restricted(1L, LONG_MAX_MASK).next();
    static final long LONG_ZERO_CONST = G.longs().next() << LONG_MASK_WIDTH;

    static final long LONG_RANDOM_CONST = G.longs().next();
    static final long LONG_RANDOM_SHIFT = G.longs().next();
    static final long LONG_RANDOM_MASK = G.longs().next();

    @Test
    public static int intSumAndMask(int i, int j) {
        return (j + i << INT_RANDOM_SHIFT + INT_RANDOM_CONST) & INT_RANDOM_MASK;
    }

    @Run(test = { "intSumAndMask" })
    public static void checkIntSumAndMask() {
        int j = G.ints().next();
        int i = G.ints().next();
        Asserts.assertEquals(intSumAndMask(i, j), (j + i << INT_RANDOM_SHIFT + INT_RANDOM_CONST) & INT_RANDOM_MASK);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    @IR(failOn = { IRNode.AND_I, IRNode.LSHIFT_I })
    public static int shiftMaskInt(int i) {
        return (i << INT_MASK_WIDTH) & INT_MASK; // transformed to: return 0;
    }

    @Check(test = "shiftMaskInt")
    public static void checkShiftMaskInt(int res) {
        Asserts.assertEquals(res, 0);
    }

    @Test
    public static long longSumAndMask(long i, long j) {
        return (j + i << LONG_RANDOM_SHIFT + LONG_RANDOM_CONST) & LONG_RANDOM_MASK;
    }

    @Run(test = { "longSumAndMask" })
    public static void checkLongSumAndMask() {
        long j = G.longs().next();
        long i = G.longs().next();
        Asserts.assertEquals(longSumAndMask(i, j), (j + i << LONG_RANDOM_SHIFT + LONG_RANDOM_CONST) & LONG_RANDOM_MASK);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_L })
    public static long shiftMaskLong(long i) {
        return (i << LONG_MASK_WIDTH) & LONG_MASK; // transformed to: return 0;
    }


    @Check(test = "shiftMaskLong")
    public static void checkShiftMaskLong(long res) {
        Asserts.assertEquals(res, 0L);
    }

    static volatile int barrier;

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    @IR(failOn = { IRNode.AND_I, IRNode.LSHIFT_I })
    public static int shiftNonConstMaskInt(int i, boolean flag) {
        int mask;
        if (flag) {
            barrier = 42;
            mask = INT_MASK;
        } else {
            mask = INT_MASK2;
        }
        return mask & (i << INT_MASK_WIDTH); // transformed to: return 0;
    }

    @Check(test = "shiftNonConstMaskInt")
    public static void checkShiftNonConstMaskInt(int res) {
        Asserts.assertEquals(res, 0);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_L })
    public static long shiftNonConstMaskLong(long i, boolean flag) {
        long mask;
        if (flag) {
            barrier = 42;
            mask = LONG_MASK;
        } else {
            mask = LONG_MASK2;
        }
        return mask & (i << LONG_MASK_WIDTH); // transformed to: return 0;
    }

    @Check(test = "shiftNonConstMaskLong")
    public static void checkShiftNonConstMaskLong(long res) {
        Asserts.assertEquals(res, 0L);
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addShiftMaskInt(int i, int j) {
        return (j + (i << INT_MASK_WIDTH)) & INT_MASK; // transformed to: return j & INT_MASK;
    }

    @Run(test = "addShiftMaskInt")
    public static void addShiftMaskInt_runner() {
        int i = G.ints().next();
        int j = G.ints().next();
        Asserts.assertEquals(addShiftMaskInt(i, j), j & INT_MASK);
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addShiftPlusConstMaskInt(int i, int j) {
        return (j + ((i + INT_RANDOM_CONST) << INT_MASK_WIDTH)) & INT_MASK; // transformed to: return j & INT_MASK;
    }

    @Run(test = "addShiftPlusConstMaskInt")
    public static void addShiftPlusConstMaskInt_runner() {
        int i = G.ints().next();
        int j = G.ints().next();
        Asserts.assertEquals(addShiftPlusConstMaskInt(i, j), j & INT_MASK);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = { IRNode.ADD_I, "2", IRNode.LSHIFT_I, "1" })
    public static int addShiftPlusConstDisjointMaskInt(int i, int j) {
        return (j + ((i + 5) << 2)) & 32; // NOT transformed even though (5<<2) & 32 == 0
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = { IRNode.ADD_I, "1", IRNode.LSHIFT_I, "1" })
    public static int addShiftPlusConstOverlyLargeShift(int i, int j) {
        return (j + i << 129) & 32; // NOT transformed, only lower 5 bits of shift count.
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addSshiftNonConstMaskInt(int i, int j, boolean flag) {
        int mask;
        if (flag) {
            barrier = 42;
            mask = INT_MASK;
        } else {
            mask = INT_MASK2;
        }
        return mask & (j + (i << INT_MASK_WIDTH)); // transformed to: return j & mask;
    }

    @Run(test = "addSshiftNonConstMaskInt")
    public static void addSshiftNonConstMaskInt_runner() {
        int i = G.ints().next();
        int j = G.ints().next();
        Asserts.assertEquals(addSshiftNonConstMaskInt(i, j, true), j & INT_MASK);
        Asserts.assertEquals(addSshiftNonConstMaskInt(i, j, false), j & INT_MASK2);
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I })
    public static int addConstNonConstMaskInt(int j, boolean flag) {
        int mask;
        if (flag) {
            barrier = 42;
            mask = INT_MASK;
        } else {
            mask = INT_MASK2;
        }
        return mask & (j + INT_ZERO_CONST); // transformed to: return j & mask;
    }

    @Run(test = "addConstNonConstMaskInt")
    public static void addConstNonConstMaskInt_runner() {
        int j = G.ints().next();
        Asserts.assertEquals(addConstNonConstMaskInt(j, true), j & INT_MASK);
        Asserts.assertEquals(addConstNonConstMaskInt(j, false), j & INT_MASK2);
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftMaskLong(long i, long j) {
        return (j + (i << LONG_MASK_WIDTH)) & LONG_MASK; // transformed to: return j & INT_MASK;
    }

    @Run(test = "addShiftMaskLong")
    public static void addShiftMaskLong_runner() {
        long i = G.longs().next();
        long j = G.longs().next();
        Asserts.assertEquals(addShiftMaskLong(i, j), j & LONG_MASK);
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftPlusConstMaskLong(long i, long j) {
        return (j + ((i + LONG_RANDOM_CONST) << LONG_MASK_WIDTH)) & LONG_MASK; // transformed to: return j & LONG_MASK;
    }

    @Run(test = "addShiftPlusConstMaskLong")
    public static void addShiftPlusConstMaskLong_runner() {
        long i = G.longs().next();
        long j = G.longs().next();
        Asserts.assertEquals(addShiftPlusConstMaskLong(i, j), j & LONG_MASK);
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addSshiftNonConstMaskLong(long i, long j, boolean flag) {
        long mask;
        if (flag) {
            barrier = 42;
            mask = LONG_MASK;
        } else {
            mask = LONG_MASK2;
        }
        return mask & (j + (i << LONG_MASK_WIDTH)); // transformed to: return j & mask
    }

    @Run(test = "addSshiftNonConstMaskLong")
    public static void addSshiftNonConstMaskLong_runner() {
        long i = G.longs().next();
        long j = G.longs().next();
        Asserts.assertEquals(addSshiftNonConstMaskLong(i, j, true), j & LONG_MASK);
        Asserts.assertEquals(addSshiftNonConstMaskLong(i, j, false), j & LONG_MASK2);
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L })
    public static long addConstNonConstMaskLong(long j, boolean flag) {
        long mask;
        if (flag) {
            barrier = 42;
            mask = LONG_MASK;
        } else {
            mask = LONG_MASK2;
        }
        return mask & (j + LONG_ZERO_CONST); // transformed to: return j & mask;
    }

    @Run(test = "addConstNonConstMaskLong")
    public static void addConstNonConstMaskLong_runner() {
        long j = G.longs().next();
        Asserts.assertEquals(addConstNonConstMaskLong(j, true), j & LONG_MASK);
        Asserts.assertEquals(addConstNonConstMaskLong(j, false), j & LONG_MASK2);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_I, IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addShiftMaskInt2(int i, int j) {
        return ((j << INT_MASK_WIDTH) + (i << INT_MASK_WIDTH)) & INT_MASK; // transformed to: return 0;
    }

    @Check(test = "addShiftMaskInt2")
    public static void checkAddShiftMaskInt2(int res) {
        Asserts.assertEquals(res, 0);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftMaskLong2(long i, long j) {
        return ((j << INT_MASK_WIDTH) + (i << INT_MASK_WIDTH)) & INT_MASK; // transformed to: return 0;
    }

    @Check(test = "addShiftMaskLong2")
    public static void checkAddShiftMaskLong2(long res) {
        Asserts.assertEquals(res, 0L);
    }

    // Try to get add inputs swapped compared to other tests
    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addShiftMaskInt3(int i, long j) {
        int add1 = (i << INT_MASK_WIDTH);
        int add2 = (int)j;
        return (add1 + add2) & INT_MASK; // transformed to: return j & INT_MASK;
    }

    @Run(test = "addShiftMaskInt3")
    public static void addShiftMaskInt3_runner() {
        int i = G.ints().next();
        int j = G.ints().next();
        Asserts.assertEquals(addShiftMaskInt3(i, j), j & INT_MASK);
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftMaskLong3(long i, float j) {
        long add1 = (i << LONG_MASK_WIDTH);
        long add2 = (long)j;
        return (add1 + add2) & LONG_MASK; // transformed to: return j & LONG_MASK;
    }

    @Run(test = "addShiftMaskLong3")
    public static void addShiftMaskLong3_runner() {
        long i = G.longs().next();
        float j = G.floats().next();
        Asserts.assertEquals(addShiftMaskLong3(i, j), ((long) j) & LONG_MASK);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_I, IRNode.CONV_I2L })
    public static long shiftConvMask(int i) {
        return ((long) (i << INT_MASK_WIDTH)) & INT_MASK; // transformed to: return 0;
    }

    @Check(test = "shiftConvMask")
    public static void checkShiftConvMask(long res) {
        Asserts.assertEquals(res, 0L);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_I, IRNode.CONV_I2L })
    public static long shiftNotConstConvMask(int i, boolean flag) {
        long mask;
        if (flag) {
            barrier = 42;
            mask = INT_MASK;
        } else {
            mask = INT_MASK2;
        }
        return mask & ((long) (i << INT_MASK_WIDTH)); // transformed to: return 0;
    }

    @Check(test = "shiftNotConstConvMask")
    public static void checkShiftNotConstConvMask(long res) {
        Asserts.assertEquals(res, 0L);
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_I, IRNode.CONV_I2L })
    public static long addShiftConvMask(int i, long j) {
        return (j + (i << INT_MASK_WIDTH)) & INT_MASK; // transformed to: return j & INT_MASK;
    }

    @Run(test = "addShiftConvMask")
    public static void addShiftConvMask_runner() {
        int i = G.ints().next();
        long j = G.longs().next();
        Asserts.assertEquals(addShiftConvMask(i, j), j & INT_MASK);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L, IRNode.LSHIFT_I, IRNode.CONV_I2L })
    public static long addShiftConvMask2(int i, int j) {
        return (((long) (j << INT_MASK_WIDTH)) + ((long) (i << INT_MASK_WIDTH))) & INT_MASK; // transformed to: return 0;
    }

    @Check(test = "addShiftConvMask2")
    public static void checkAddShiftConvMask2(long res) {
        Asserts.assertEquals(res, 0L);
    }

    @Test
    @IR(failOn = { IRNode.AND_I })
    public static int shiftMaskIntCheckIndex(int i, int length) {
        return Objects.checkIndex(i << INT_MASK_WIDTH, length) & INT_MASK; // transformed to: return 0;
    }

    @Run(test = "shiftMaskIntCheckIndex")
    public static void shiftMaskIntCheckIndex_runner() {
        int i = G.uniformInts(0, ((Integer.MAX_VALUE - 1) >> INT_MASK_WIDTH) - 1).next();
        Asserts.assertEquals(shiftMaskIntCheckIndex(i, (i << INT_MASK_WIDTH) + 1), 0);
    }

    @Test
    @IR(failOn = { IRNode.AND_L })
    public static long shiftMaskLongCheckIndex(long i, long length) {
        return Objects.checkIndex(i << LONG_MASK_WIDTH, length) & LONG_MASK; // transformed to: return 0;
    }

    @Run(test = "shiftMaskLongCheckIndex")
    public static void shiftMaskLongCheckIndex_runner() {
        long i = G.uniformLongs(0L, ((Long.MAX_VALUE - 1) >> LONG_MASK_WIDTH) - 1).next();
        Asserts.assertEquals(shiftMaskLongCheckIndex(i, (i << LONG_MASK_WIDTH) + 1), 0L);
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I })
    public static int addShiftMaskIntCheckIndex(int i, int j, int length) {
        return (j + Objects.checkIndex(i << INT_MASK_WIDTH, length)) & INT_MASK; // transformed to: return j & INT_MASK;
    }

    @Run(test = "addShiftMaskIntCheckIndex")
    public static void addShiftMaskIntCheckIndex_runner() {
        int i = G.uniformInts(0, ((Integer.MAX_VALUE - 1) >> INT_MASK_WIDTH) - 1).next();
        int j = G.ints().next();
        Asserts.assertEquals(addShiftMaskIntCheckIndex(i, j, (i << INT_MASK_WIDTH) + 1), j & INT_MASK);
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L })
    public static long addShiftMaskLongCheckIndex(long i, long j, long length) {
        return (j + Objects.checkIndex(i << LONG_MASK_WIDTH, length)) & LONG_MASK; // transformed to: return j & LONG_MASK;
    }

    @Run(test = "addShiftMaskLongCheckIndex")
    public static void addShiftMaskLongCheckIndex_runner() {
        long i = G.uniformLongs(0L, ((Long.MAX_VALUE - 1) >> LONG_MASK_WIDTH) - 1).next();
        long j = G.longs().next();
        Asserts.assertEquals(addShiftMaskLongCheckIndex(i, j, (i << LONG_MASK_WIDTH) + 1), j & LONG_MASK);
    }

    @Test
    @IR(failOn = { IRNode.AND_I, IRNode.ADD_I })
    public static int addShiftMaskIntCheckIndex2(int i, int j, int length) {
        return (Objects.checkIndex(j << INT_MASK_WIDTH, length) + Objects.checkIndex(i << INT_MASK_WIDTH, length)) & INT_MASK; // transformed to: return 0;
    }


    @Run(test = "addShiftMaskIntCheckIndex2")
    public static void addShiftMaskIntCheckIndex2_runner() {
        int i = G.uniformInts(0, ((Integer.MAX_VALUE - 1) >> INT_MASK_WIDTH) - 1).next();
        int j = G.uniformInts(0, ((Integer.MAX_VALUE - 1) >> INT_MASK_WIDTH) - 1).next();
        Asserts.assertEquals(addShiftMaskIntCheckIndex2(i, j, (Integer.max(i, j) << INT_MASK_WIDTH) + 1), 0);
    }

    @Test
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L })
    public static long addShiftMaskLongCheckIndex2(long i, long j, long length) {
        return (Objects.checkIndex(j << LONG_MASK_WIDTH, length) + Objects.checkIndex(i << LONG_MASK_WIDTH, length)) & LONG_MASK; // transformed to: return 0;
    }

    @Run(test = "addShiftMaskLongCheckIndex2")
    public static void addShiftMaskLongCheckIndex2_runner() {
        long i = G.uniformLongs(0L, ((Long.MAX_VALUE - 1) >> LONG_MASK_WIDTH) - 1).next();
        long j = G.uniformLongs(0L, ((Long.MAX_VALUE - 1) >> LONG_MASK_WIDTH) - 1).next();
        Asserts.assertEquals(addShiftMaskLongCheckIndex2(i, j, (Long.max(i, j) << LONG_MASK_WIDTH) + 1), 0L);
    }

    @Test
    @IR(failOn = { IRNode.AND_L, IRNode.CONV_I2L })
    public static long shiftConvMaskCheckIndex(int i, int length) {
        return ((long) Objects.checkIndex(i << INT_MASK_WIDTH, length)) & INT_MASK; // transformed to: return 0;
    }

    @Run(test = "shiftConvMaskCheckIndex")
    public static void shiftConvMaskCheckIndex_runner() {
        int i = G.uniformInts(0, ((Integer.MAX_VALUE - 1) >> INT_MASK_WIDTH) - 1).next();
        Asserts.assertEquals(shiftConvMaskCheckIndex(i, (i << INT_MASK_WIDTH) + 1), 0L);
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.CONV_I2L })
    public static long addShiftConvMaskCheckIndex(int i, long j, int length) {
        return (j + Objects.checkIndex(i << INT_MASK_WIDTH, length)) & INT_MASK; // transformed to: return j & INT_MASK;
    }

    @Run(test = "addShiftConvMaskCheckIndex")
    public static void addShiftConvMaskCheckIndex_runner() {
        int i = G.uniformInts(0, ((Integer.MAX_VALUE - 1) >> INT_MASK_WIDTH) - 1).next();
        long j = G.longs().next();
        Asserts.assertEquals(addShiftConvMaskCheckIndex(i, j, (i << INT_MASK_WIDTH) + 1), j & INT_MASK);
    }

    @Test
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L })
    public static long addShiftConvMaskCheckIndex2(int i, int j, int length) {
        return (((long) Objects.checkIndex(j << INT_MASK_WIDTH, length)) + ((long) Objects.checkIndex(i << INT_MASK_WIDTH, length))) & INT_MASK; // transformed to: return 0;
    }

    @Run(test = "addShiftConvMaskCheckIndex2")
    public static void addShiftConvMaskCheckIndex2_runner() {
        int i = G.uniformInts(0, ((Integer.MAX_VALUE - 1) >> INT_MASK_WIDTH) - 1).next();
        int j = G.uniformInts(0, ((Integer.MAX_VALUE - 1) >> INT_MASK_WIDTH) - 1).next();
        Asserts.assertEquals(addShiftConvMaskCheckIndex2(i, j, (Integer.max(i, j) << INT_MASK_WIDTH) + 1), 0L);
    }

    @Test
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.LSHIFT_I, "1"})
    public int shiftLeftWithLowMaskInt(int x) {
      return (x << INT_MASK_WIDTH) & (-1 << INT_MASK_WIDTH); // transformed to: return x << INT_MASK_WIDTH;
    }

    @Test
    @IR(failOn = {IRNode.AND_L})
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    public long shiftLeftWithLowMaskLong(long x) {
      return (x << LONG_MASK_WIDTH) & (-1 << LONG_MASK_WIDTH); // transformed to: return x << LONG_MASK_WIDTH;
    }

    @Test
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.LSHIFT_I, "1"})
    public int shiftLeftWithLowMaskSmallInt(int x) {
        return (x << INT_MASK_WIDTH) & (-1 << (INT_MASK_WIDTH - 1)); // transformed to: return x << INT_MASK_WIDTH;
    }

    @Test
    @IR(failOn = {IRNode.AND_L})
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    public long shiftLeftWithLowMaskSmallLong(long x) {
        return (x << LONG_MASK_WIDTH) & (-1 << (LONG_MASK_WIDTH - 1)); // transformed to: return x << LONG_MASK_WIDTH;
    }

    @Test
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.LSHIFT_I, "1"})
    public static int shiftLeftWithLowMaskIntReversed(int x) {
      return (-1 << INT_MASK_WIDTH) & (x << INT_MASK_WIDTH); // transformed to: return x << INT_MASK_WIDTH;
    }

    @Test
    @IR(failOn = {IRNode.AND_L})
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    public static long shiftLeftWithLowMaskLongReversed(long x) {
      return (-1 << LONG_MASK_WIDTH) & (x << LONG_MASK_WIDTH); // transformed to: return x << LONG_MASK_WIDTH;
    }

    @Test
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.LSHIFT_I, "1"})
    public static int shiftLeftWithLowMaskSmallIntReversed(int x) {
        return (-1 << (INT_MASK_WIDTH - 1)) & (x << INT_MASK_WIDTH); // transformed to: return x << INT_MASK_WIDTH;
    }

    @Test
    @IR(failOn = {IRNode.AND_L})
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    public static long shiftLeftWithLowMaskSmallLongReversed(long x) {
        return (-1 << (LONG_MASK_WIDTH - 1)) & (x << LONG_MASK_WIDTH); // transformed to: return x << LONG_MASK_WIDTH;
    }

    @Test
    @IR(counts = {IRNode.AND_I, "1"})
    public int andMaskNonNegativeInt(int x) {
        return (x & 0x7FFF) & 0xFFFF; // transformed to: return x & 0x7FFF;
    }

    @Test
    @IR(counts = {IRNode.AND_L, "1"})
    public long andMaskNonNegativeLong(long x) {
        return (x & 0x7FFFL) & 0xFFFFL; // transformed to: return x & 0x7FFFL;
    }

    @Test
    @IR(counts = {IRNode.AND_I, "1"})
    public int andMaskNonNegativeIntReversed(int x) {
        return 0xFFFF & (x & 0x7FFF); // transformed to: return x & 0x7FFF;
    }

    @Test
    @IR(counts = {IRNode.AND_L, "1"})
    public long andMaskNonNegativeLongReversed(long x) {
        return 0xFFFFL & (x & 0x7FFFL); // transformed to: return x & 0x7FFFL;
    }

    @Test
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.URSHIFT_I, "1"})
    public int andAfterURShiftInt(int x) {
        return (x >>> 8) & 0x00FFFFFF; // transformed to return x >>> 8;
    }

    @Test
    @IR(failOn = {IRNode.AND_L})
    @IR(counts = {IRNode.URSHIFT_L, "1"})
    public long andAfterURShiftLong(long x) {
        return (x >>> 16) & 0x0000FFFFFFFFFFFFL; // transformed to return x >>> 16;
    }

    @Test
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.URSHIFT_I, "1"})
    public int andAfterURShiftIntReversed(int x) {
        return 0x00FFFFFF & (x >>> 8); // transformed to return x >>> 8;
    }

    @Test
    @IR(failOn = {IRNode.AND_L})
    @IR(counts = {IRNode.URSHIFT_L, "1"})
    public long andAfterURShiftLongReversed(long x) {
        return 0x0000FFFFFFFFFFFFL & (x >>> 16); // transformed to return x >>> 16;
    }

    @Run(test = {"shiftLeftWithLowMaskInt", "shiftLeftWithLowMaskLong",
                 "shiftLeftWithLowMaskSmallInt", "shiftLeftWithLowMaskSmallLong",
                 "shiftLeftWithLowMaskIntReversed", "shiftLeftWithLowMaskLongReversed",
                 "shiftLeftWithLowMaskSmallIntReversed", "shiftLeftWithLowMaskSmallLongReversed",
                 "andMaskNonNegativeInt", "andMaskNonNegativeLong",
                 "andMaskNonNegativeIntReversed", "andMaskNonNegativeLongReversed",
                 "andAfterURShiftInt", "andAfterURShiftLong",
                 "andAfterURShiftIntReversed", "andAfterURShiftLongReversed",
    })
    public void verifyShiftAndMaskTransforms() {
        int xi = G.ints().next();
        long xl = G.longs().next();

        Asserts.assertEquals(shiftLeftWithLowMaskInt(xi), (xi << INT_MASK_WIDTH));
        Asserts.assertEquals(shiftLeftWithLowMaskLong(xl), (xl << LONG_MASK_WIDTH));
        Asserts.assertEquals(shiftLeftWithLowMaskSmallInt(xi), (xi << INT_MASK_WIDTH));
        Asserts.assertEquals(shiftLeftWithLowMaskSmallLong(xl), (xl << LONG_MASK_WIDTH));
        Asserts.assertEquals(shiftLeftWithLowMaskIntReversed(xi), (xi << INT_MASK_WIDTH));
        Asserts.assertEquals(shiftLeftWithLowMaskLongReversed(xl), (xl << LONG_MASK_WIDTH));
        Asserts.assertEquals(shiftLeftWithLowMaskSmallIntReversed(xi), (xi << INT_MASK_WIDTH));
        Asserts.assertEquals(shiftLeftWithLowMaskSmallLongReversed(xl), (xl << LONG_MASK_WIDTH));
        Asserts.assertEquals(andMaskNonNegativeInt(xi), (xi & 0x7FFF));
        Asserts.assertEquals(andMaskNonNegativeLong(xl), (xl & 0x7FFFL));
        Asserts.assertEquals(andMaskNonNegativeIntReversed(xi), (xi & 0x7FFF));
        Asserts.assertEquals(andMaskNonNegativeLongReversed(xl), (xl & 0x7FFFL));
        Asserts.assertEquals(andAfterURShiftInt(xi), (xi >>> 8));
        Asserts.assertEquals(andAfterURShiftLong(xl), (xl >>> 16));
        Asserts.assertEquals(andAfterURShiftIntReversed(xi), (xi >>> 8));
        Asserts.assertEquals(andAfterURShiftLongReversed(xl), (xl >>> 16));
    }
}
