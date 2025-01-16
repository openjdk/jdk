/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
import jdk.test.lib.Utils;
import java.util.Random;
import java.util.Objects;

/*
 * @test
 * @bug 8277850 8278949 8285793
 * @summary C2: optimize mask checks in counted loops
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestShiftAndMask
 */

public class TestShiftAndMask {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    @IR(failOn = { IRNode.AND_I, IRNode.LSHIFT_I })
    public static int shiftMaskInt(int i) {
        return (i << 2) & 3; // transformed to: return 0;
    }

    @Check(test = "shiftMaskInt")
    public static void checkShiftMaskInt(int res) {
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_L })
    public static long shiftMaskLong(long i) {
        return (i << 2) & 3; // transformed to: return 0;
    }


    @Check(test = "shiftMaskLong")
    public static void checkShiftMaskLong(long res) {
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    static volatile int barrier;

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    @IR(failOn = { IRNode.AND_I, IRNode.LSHIFT_I })
    public static int shiftNonConstMaskInt(int i, boolean flag) {
        int mask;
        if (flag) {
            barrier = 42;
            mask = 3;
        } else {
            mask = 1;
        }
        return mask & (i << 2); // transformed to: return 0;
    }

    @Check(test = "shiftNonConstMaskInt")
    public static void checkShiftNonConstMaskInt(int res) {
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_L })
    public static long shiftNonConstMaskLong(long i, boolean flag) {
        long mask;
        if (flag) {
            barrier = 42;
            mask = 3;
        } else {
            mask = 1;
        }
        return mask & (i << 2); // transformed to: return 0;
    }

    @Check(test = "shiftNonConstMaskLong")
    public static void checkShiftNonConstMaskLong(long res) {
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addShiftMaskInt(int i, int j) {
        return (j + (i << 2)) & 3; // transformed to: return j & 3;
    }

    @Run(test = "addShiftMaskInt")
    public static void addShiftMaskInt_runner() {
        int i = RANDOM.nextInt();
        int j = RANDOM.nextInt();
        int res = addShiftMaskInt(i, j);
        if (res != (j & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addSshiftNonConstMaskInt(int i, int j, boolean flag) {
        int mask;
        if (flag) {
            barrier = 42;
            mask = 3;
        } else {
            mask = 1;
        }
        return mask & (j + (i << 2)); // transformed to: return j & mask;
    }

    @Run(test = "addSshiftNonConstMaskInt")
    public static void addSshiftNonConstMaskInt_runner() {
        int i = RANDOM.nextInt();
        int j = RANDOM.nextInt();
        int res = addSshiftNonConstMaskInt(i, j, true);
        if (res != (j & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
        res = addSshiftNonConstMaskInt(i, j, false);
        if (res != (j & 1)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftMaskLong(long i, long j) {
        return (j + (i << 2)) & 3; // transformed to: return j & 3;
    }

    @Run(test = "addShiftMaskLong")
    public static void addShiftMaskLong_runner() {
        long i = RANDOM.nextLong();
        long j = RANDOM.nextLong();
        long res = addShiftMaskLong(i, j);
        if (res != (j & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addSshiftNonConstMaskLong(long i, long j, boolean flag) {
        int mask;
        if (flag) {
            barrier = 42;
            mask = 3;
        } else {
            mask = 1;
        }
        return mask & (j + (i << 2)); // transformed to: return j & mask;
    }

    @Run(test = "addSshiftNonConstMaskLong")
    public static void addSshiftNonConstMaskLong_runner() {
        long i = RANDOM.nextLong();
        long j = RANDOM.nextLong();
        long res = addSshiftNonConstMaskLong(i, j, true);
        if (res != (j & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
        res = addSshiftNonConstMaskLong(i, j, false);
        if (res != (j & 1)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_I, IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addShiftMaskInt2(int i, int j) {
        return ((j << 2) + (i << 2)) & 3; // transformed to: return 0;
    }

    @Check(test = "addShiftMaskInt2")
    public static void checkAddShiftMaskInt2(int res) {
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftMaskLong2(long i, long j) {
        return ((j << 2) + (i << 2)) & 3; // transformed to: return 0;
    }

    @Check(test = "addShiftMaskLong2")
    public static void checkAddShiftMaskLong2(long res) {
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    // Try to get add inputs swapped compared to other tests
    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addShiftMaskInt3(int i, long j) {
        int add1 = (i << 2);
        int add2 = (int)j;
        return (add1 + add2) & 3; // transformed to: return j & 3;
    }

    @Run(test = "addShiftMaskInt3")
    public static void addShiftMaskInt3_runner() {
        int i = RANDOM.nextInt();
        int j = RANDOM.nextInt();
        int res = addShiftMaskInt3(i, j);
        if (res != (j & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftMaskLong3(long i, float j) {
        long add1 = (i << 2);
        long add2 = (long)j;
        return (add1 + add2) & 3; // transformed to: return j & 3;
    }

    @Run(test = "addShiftMaskLong3")
    public static void addShiftMaskLong3_runner() {
        long i = RANDOM.nextLong();
        float j = RANDOM.nextFloat();
        long res = addShiftMaskLong3(i, j);
        if (res != (((long)j) & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_I, IRNode.CONV_I2L })
    public static long shiftConvMask(int i) {
        return ((long)(i << 2)) & 3; // transformed to: return 0;
    }

    @Check(test = "shiftConvMask")
    public static void checkShiftConvMask(long res) {
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_I, IRNode.CONV_I2L })
    public static long shiftNotConstConvMask(int i, boolean flag) {
        long mask;
        if (flag) {
            barrier = 42;
            mask = 3;
        } else {
            mask = 1;
        }
        return mask & ((long)(i << 2)); // transformed to: return 0;
    }

    @Check(test = "shiftNotConstConvMask")
    public static void checkShiftNotConstConvMask(long res) {
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_I, IRNode.CONV_I2L })
    public static long addShiftConvMask(int i, long j) {
        return (j + (i << 2)) & 3; // transformed to: return j & 3;
    }

    @Run(test = "addShiftConvMask")
    public static void addShiftConvMask_runner() {
        int i = RANDOM.nextInt();
        long j = RANDOM.nextLong();
        long res = addShiftConvMask(i, j);
        if (res != (j & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L, IRNode.LSHIFT_I, IRNode.CONV_I2L })
    public static long addShiftConvMask2(int i, int j) {
        return (((long)(j << 2)) + ((long)(i << 2))) & 3; // transformed to: return 0;
    }

    @Check(test = "addShiftConvMask2")
    public static void checkAddShiftConvMask2(long res) {
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(failOn = { IRNode.AND_I })
    public static int shiftMaskIntCheckIndex(int i, int length) {
        return Objects.checkIndex(i << 2, length) & 3; // transformed to: return 0;
    }

    @Run(test = "shiftMaskIntCheckIndex")
    public static void shiftMaskIntCheckIndex_runner() {
        int i = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        int res = shiftMaskIntCheckIndex(i, (i << 2) + 1);
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(failOn = { IRNode.AND_L })
    public static long shiftMaskLongCheckIndex(long i, long length) {
        return Objects.checkIndex(i << 2, length) & 3; // transformed to: return 0;
    }

    @Run(test = "shiftMaskLongCheckIndex")
    public static void shiftMaskLongCheckIndex_runner() {
        long i = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        long res = shiftMaskLongCheckIndex(i, (i << 2) + 1);
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I })
    public static int addShiftMaskIntCheckIndex(int i, int j, int length) {
        return (j + Objects.checkIndex(i << 2, length)) & 3; // transformed to: return j & 3;
    }

    @Run(test = "addShiftMaskIntCheckIndex")
    public static void addShiftMaskIntCheckIndex_runner() {
        int i = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        int j = RANDOM.nextInt();
        int res = addShiftMaskIntCheckIndex(i, j, (i << 2) + 1);
        if (res != (j & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L })
    public static long addShiftMaskLongCheckIndex(long i, long j, long length) {
        return (j + Objects.checkIndex(i << 2, length)) & 3; // transformed to: return j & 3;
    }

    @Run(test = "addShiftMaskLongCheckIndex")
    public static void addShiftMaskLongCheckIndex_runner() {
        long i = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        long j = RANDOM.nextLong();
        long res = addShiftMaskLongCheckIndex(i, j, (i << 2) + 1);
        if (res != (j & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(failOn = { IRNode.AND_I, IRNode.ADD_I })
    public static int addShiftMaskIntCheckIndex2(int i, int j, int length) {
        return (Objects.checkIndex(j << 2, length) + Objects.checkIndex(i << 2, length)) & 3; // transformed to: return 0;
    }


    @Run(test = "addShiftMaskIntCheckIndex2")
    public static void addShiftMaskIntCheckIndex2_runner() {
        int i = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        int j = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        int res = addShiftMaskIntCheckIndex2(i, j, (Integer.max(i, j) << 2) + 1);
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L })
    public static long addShiftMaskLongCheckIndex2(long i, long j, long length) {
        return (Objects.checkIndex(j << 2, length) + Objects.checkIndex(i << 2, length)) & 3; // transformed to: return 0;
    }

    @Run(test = "addShiftMaskLongCheckIndex2")
    public static void addShiftMaskLongCheckIndex2_runner() {
        long i = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        long j = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        long res = addShiftMaskLongCheckIndex2(i, j, (Long.max(i, j) << 2) + 1);
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(failOn = { IRNode.AND_L, IRNode.CONV_I2L })
    public static long shiftConvMaskCheckIndex(int i, int length) {
        return ((long)Objects.checkIndex(i << 2, length)) & 3; // transformed to: return 0;
    }

    @Run(test = "shiftConvMaskCheckIndex")
    public static void shiftConvMaskCheckIndex_runner() {
        int i = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        long res = shiftConvMaskCheckIndex(i, (i << 2) + 1);
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.CONV_I2L })
    public static long addShiftConvMaskCheckIndex(int i, long j, int length) {
        return (j + Objects.checkIndex(i << 2, length)) & 3; // transformed to: return j & 3;
    }

    @Run(test = "addShiftConvMaskCheckIndex")
    public static void addShiftConvMaskCheckIndex_runner() {
        int i = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        long j = RANDOM.nextLong();
        long res = addShiftConvMaskCheckIndex(i, j, (i << 2) + 1);
        if (res != (j & 3)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L })
    public static long addShiftConvMaskCheckIndex2(int i, int j, int length) {
        return (((long)Objects.checkIndex(j << 2, length)) + ((long)Objects.checkIndex(i << 2, length))) & 3; // transformed to: return 0;
    }

    @Run(test = "addShiftConvMaskCheckIndex2")
    public static void addShiftConvMaskCheckIndex2_runner() {
        int i = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        int j = RANDOM.nextInt((Integer.MAX_VALUE - 1) >> 2);
        long res = addShiftConvMaskCheckIndex2(i, j, (Integer.max(i, j) << 2) + 1);
        if (res != 0) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }
}
