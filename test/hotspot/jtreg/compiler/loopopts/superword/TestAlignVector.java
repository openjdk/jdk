/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

// TODO bug etc
// TODO run with and without -XX:-TieredCompilation -Xbatch

/*
 * @test
 * @bug 8253191
 *
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *
 * @run main/bootclasspath/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                                 -XX:+AlignVector -XX:+VerifyAlignVector
 *                                 -XX:LoopUnrollLimit=10000
 *                                 -XX:CompileCommand=VectorizeDebug,compiler.loopopts.superword.TestAlignVector::test*,128
 *                                 -XX:CompileCommand=printcompilation,compiler.loopopts.superword.TestAlignVector::*
 *                                 compiler.loopopts.superword.TestAlignVector
 */
package compiler.loopopts.superword;

import java.lang.invoke.*;
//// TODO remove
//// import jdk.internal.vm.annotation.DontInline;
//// import jdk.test.lib.Asserts;
import java.util.Random;
import jdk.test.lib.Utils;

// TODO:
// types: single and in combo
// hand unrolled - especially relevant for stride and scale other than 1/-1
//
// Also: different scenarios: +-AlignVector, +-VerifyAlignVector, different MaxVectorSize
//
// And: Unsafe accesses. Misaligned also.
//
// Is there a way to clear the profiling somehow?
//
// TODO: Result verification: random input, copy, run with compiled and interpreted, compare.
//
// TODO: benchmark no alignment if not required: benefits vs overhead?
//       -> more for future: in this change we should still align everything we can

public class TestAlignVector {

    static final int RANGE_CON = 1024 * 8;
    static final int ITERATIONS = 10;

    private static final Random random = Utils.getRandomInstance();

    // Setup for variable compile-time constants:
    private static final CallSite INIT_CS    = new MutableCallSite(MethodType.methodType(int.class));
    private static final CallSite LIMIT_CS   = new MutableCallSite(MethodType.methodType(int.class));
    private static final CallSite STRIDE_CS  = new MutableCallSite(MethodType.methodType(int.class));
    private static final CallSite SCALE_CS   = new MutableCallSite(MethodType.methodType(int.class));
    private static final CallSite OFFSET1_CS = new MutableCallSite(MethodType.methodType(int.class));
    private static final CallSite OFFSET2_CS = new MutableCallSite(MethodType.methodType(int.class));
    private static final CallSite OFFSET3_CS = new MutableCallSite(MethodType.methodType(int.class));
    private static final MethodHandle INIT_MH    = INIT_CS.dynamicInvoker();
    private static final MethodHandle LIMIT_MH   = LIMIT_CS.dynamicInvoker();
    private static final MethodHandle STRIDE_MH  = STRIDE_CS.dynamicInvoker();
    private static final MethodHandle SCALE_MH   = SCALE_CS.dynamicInvoker();
    private static final MethodHandle OFFSET1_MH = OFFSET1_CS.dynamicInvoker();
    private static final MethodHandle OFFSET2_MH = OFFSET2_CS.dynamicInvoker();
    private static final MethodHandle OFFSET3_MH = OFFSET3_CS.dynamicInvoker();

    // Toggle if init, limit and offset are constants or variables
    private static final CallSite INIT_IS_CON_CS   = new MutableCallSite(MethodType.methodType(boolean.class));
    private static final CallSite LIMIT_IS_CON_CS  = new MutableCallSite(MethodType.methodType(boolean.class));
    private static final CallSite OFFSET1_IS_CON_CS = new MutableCallSite(MethodType.methodType(boolean.class));
    private static final CallSite OFFSET2_IS_CON_CS = new MutableCallSite(MethodType.methodType(boolean.class));
    private static final CallSite OFFSET3_IS_CON_CS = new MutableCallSite(MethodType.methodType(boolean.class));
    private static final MethodHandle INIT_IS_CON_MH    = INIT_IS_CON_CS.dynamicInvoker();
    private static final MethodHandle LIMIT_IS_CON_MH   = LIMIT_IS_CON_CS.dynamicInvoker();
    private static final MethodHandle OFFSET1_IS_CON_MH = OFFSET1_IS_CON_CS.dynamicInvoker();
    private static final MethodHandle OFFSET2_IS_CON_MH = OFFSET2_IS_CON_CS.dynamicInvoker();
    private static final MethodHandle OFFSET3_IS_CON_MH = OFFSET3_IS_CON_CS.dynamicInvoker();

    // Hand-Unrolling compile-constants
    private static final CallSite HAND_UNROLLING1_CS = new MutableCallSite(MethodType.methodType(int.class));
    private static final CallSite HAND_UNROLLING2_CS = new MutableCallSite(MethodType.methodType(int.class));
    private static final CallSite HAND_UNROLLING3_CS = new MutableCallSite(MethodType.methodType(int.class));
    private static final MethodHandle HAND_UNROLLING1_MH = HAND_UNROLLING1_CS.dynamicInvoker();
    private static final MethodHandle HAND_UNROLLING2_MH = HAND_UNROLLING2_CS.dynamicInvoker();
    private static final MethodHandle HAND_UNROLLING3_MH = HAND_UNROLLING3_CS.dynamicInvoker();

    static void setConstant(CallSite cs, int value) {
        MethodHandle constant = MethodHandles.constant(int.class, value);
        cs.setTarget(constant);
    }

    static void setConstant(CallSite cs, boolean value) {
        MethodHandle constant = MethodHandles.constant(boolean.class, value);
        cs.setTarget(constant);
    }

    static int init_con() { // compile-time constant
        try {
            return (int) INIT_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static boolean init_is_con() { // compile-time constant
        try {
            return (boolean) INIT_IS_CON_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int init_con_or_var() {
        int init = init_con();
        if (!init_is_con()) { // branch constant folds to true or false
            init += random.nextInt(64);
        }
        return init;
    }

    static int limit_con() { // compile-time constant
        try {
            return (int) LIMIT_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static boolean limit_is_con() { // compile-time constant
        try {
            return (boolean) LIMIT_IS_CON_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int limit_con_or_var() {
        int limit = limit_con();
        if (!limit_is_con()) { // branch constant folds to true or false
            limit -= random.nextInt(64);
        }
        return limit;
    }

    static int stride_con() { // compile-time constant
        try {
            return (int) STRIDE_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int scale_con() { // compile-time constant
        try {
            return (int) SCALE_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int offset1_con() { // compile-time constant
        try {
            return (int) OFFSET1_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int offset2_con() { // compile-time constant
        try {
            return (int) OFFSET2_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int offset3_con() { // compile-time constant
        try {
            return (int) OFFSET3_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static boolean offset1_is_con() { // compile-time constant
        try {
            return (boolean) OFFSET1_IS_CON_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static boolean offset2_is_con() { // compile-time constant
        try {
            return (boolean) OFFSET2_IS_CON_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static boolean offset3_is_con() { // compile-time constant
        try {
            return (boolean) OFFSET3_IS_CON_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int offset1_con_or_var() {
        int offset = offset1_con();
        if (!offset1_is_con()) { // branch constant folds to true or false
            offset += random.nextInt(64);
        }
        return offset;
    }

    static int offset2_con_or_var() {
        int offset = offset2_con();
        if (!offset2_is_con()) { // branch constant folds to true or false
            offset += random.nextInt(64);
        }
        return offset;
    }

    static int offset3_con_or_var() {
        int offset = offset3_con();
        if (!offset3_is_con()) { // branch constant folds to true or false
            offset += random.nextInt(64);
        }
        return offset;
    }

    static int opposite_direction_offset1_con_or_var() {
        // When indexing in the opposite direction to i, we Want to have:
        //
        //   a[x - i * scale]
        //
        // So we want to fulfill these constraints:
        //
        //   x - init * scale  = offset + limit * scale
        //   x - limit * scale = offset + init * scale
        //
        // Hence:
        //
        //   x = offset + limit * scale + init * scale;

        int offset = offset1_con_or_var();
        int init = init_con();
        int limit = limit_con();
        int scale = scale_con();
        return offset + limit * scale + init * scale;
    }

    static int opposite_direction_offset2_con_or_var() {
        int offset = offset2_con_or_var();
        int init = init_con();
        int limit = limit_con();
        int scale = scale_con();
        return offset + limit * scale + init * scale;
    }

    static int opposite_direction_offset3_con_or_var() {
        int offset = offset3_con_or_var();
        int init = init_con();
        int limit = limit_con();
        int scale = scale_con();
        return offset + limit * scale + init * scale;
    }

    static int hand_unrolling1_con() { // compile-time constant
        try {
            return (int) HAND_UNROLLING1_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int hand_unrolling2_con() { // compile-time constant
        try {
            return (int) HAND_UNROLLING2_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int hand_unrolling3_con() { // compile-time constant
        try {
            return (int) HAND_UNROLLING3_MH.invokeExact();
        } catch (Throwable t) {
            throw new InternalError(t); // should NOT happen
        }
    }

    static int randomStride() {
        return switch (random.nextInt(6)) {
            case 0       -> random.nextInt(64) + 1; // [1..64]
            case 1, 2, 3 -> 1;
            default      -> 1 << random.nextInt(7); // powers of 2: 1..64
        };
    }

    static int randomScale() {
        return switch (random.nextInt(6)) {
            case 0       -> random.nextInt(64) + 1; // [1..64]
            case 1, 2, 3 -> 1;
            default      -> 1 << random.nextInt(7); // powers of 2: 1..64
        };
    }

    static int randomOffsetDiff() {
        return switch (random.nextInt(6)) {
            case 0       -> random.nextInt(256) + 128;
            case 1, 2, 3 -> 0;
            case 4       -> +(1 << random.nextInt(8)); // powers of 2: 1..128
            default      -> -(1 << random.nextInt(8)); // powers of 2: -1..-128
        };
    }

    static int randomHandUnrolling() {
        return switch (random.nextInt(2)) {
            case 0       -> random.nextInt(16) + 1; // [1..16]
            default      -> 1 << random.nextInt(5); // powers of 2: 1..16
        };
    }

    static void setRandomConstants() {
        // We want to create random constants for a loop, but they should never go out of bounds.
        // We constrain i to be in the range [init..limit], with init < limit. For simplicity, we
        // always generate:
        // 
        //   1 <= scale  <= 64
        //   1 <= stride <= 64
        //
        // We work with this reference memory access:
        //
        //   a[offset + i * scale]
        //
        // It is up to the test function to re-arrange the the given terms to iterate upward or
        // downward, to hand-unroll etc.
        //
        // We must ensure that the first and last indices are in range:
        //
        //   0 + error <= offset + init * scale
        //   offset + limit * scale < range - error
        //
        // The "error" term is there such that the test functions have the freedom to slightly
        // diverge from the reference memory access pattern (for example modify the offset).
        //
        // The values for scale and range are already fixed. We now want to generate values for
        // offset, init and limit.
        //
        // (1) Fix offset:
        //
        //     init >= (error - offset) / scale
        //     limit < (range - error - offset) / scale
        //
        // (2) Fix init:
        //
        //     offset >= error - init * scale
        //     limit < (range - error - offset) / scale
        //     
        // (3) Fix limit:
        //
        //     offset < range - error - limit * scale
        //     init >= (error - offset) / scale
        //
        // We can still slightly perturb the results in the direction permitted by the inequality.

        int stride = randomStride();
        int scale = randomScale();
        int range = RANGE_CON;
        int error = 1024; // generous
        int init;
        int limit;
        int offset1;
        switch(random.nextInt(3)) {
            case 0 -> {
                offset1 = random.nextInt(2_000_000) - 1_000_000;
                init = (error - offset1) / scale + random.nextInt(64);
                limit = (range - error - offset1) / scale - random.nextInt(64);
            }
            case 1 -> {
                init = random.nextInt(2_000_000) - 1_000_000;
                offset1 = error - init * scale + random.nextInt(64);
                limit = (range - error - offset1) / scale - random.nextInt(64);
            }
            default -> {
                limit = random.nextInt(2_000_000) - 1_000_000;
                offset1 = range - error - limit * scale - random.nextInt(64);
                init = (error - offset1) / scale + random.nextInt(64);
            }
        }

        int offset2 = offset1 + randomOffsetDiff();
        int offset3 = offset1 + randomOffsetDiff();

        // We can toggle the init, limit and offset to either be constant or variable:
        boolean init_is_con   = random.nextInt(3) != 0;
        boolean limit_is_con  = random.nextInt(3) != 0;
        boolean offset1_is_con = random.nextInt(3) != 0;
        boolean offset2_is_con = random.nextInt(3) != 0;
        boolean offset3_is_con = random.nextInt(3) != 0;

        int hand_unrolling1 = randomHandUnrolling();
        int hand_unrolling2 = randomHandUnrolling();
        int hand_unrolling3 = randomHandUnrolling();

        System.out.println("  init:    " + init    + " (con: " + init_is_con + ")");
        System.out.println("  limit:   " + limit   + " (con: " + limit_is_con + ")");
        System.out.println("  offset1: " + offset1 + " (con: " + offset1_is_con + ")");
        System.out.println("  offset2: " + offset2 + " (con: " + offset2_is_con + ")");
        System.out.println("  offset3: " + offset3 + " (con: " + offset3_is_con + ")");
        System.out.println("  stride:  " + stride);
        System.out.println("  scale:   " + scale);
        System.out.println("  hand_unrolling1: " + hand_unrolling1);
        System.out.println("  hand_unrolling2: " + hand_unrolling2);
        System.out.println("  hand_unrolling3: " + hand_unrolling3);
        setConstant(INIT_CS,   init);
        setConstant(LIMIT_CS,  limit);
        setConstant(STRIDE_CS, stride);
        setConstant(SCALE_CS,  scale);
        setConstant(OFFSET1_CS, offset1);
        setConstant(OFFSET2_CS, offset2);
        setConstant(OFFSET3_CS, offset3);
        setConstant(INIT_IS_CON_CS,   init_is_con);
        setConstant(LIMIT_IS_CON_CS,  limit_is_con);
        setConstant(OFFSET1_IS_CON_CS, offset1_is_con);
        setConstant(OFFSET2_IS_CON_CS, offset2_is_con);
        setConstant(OFFSET3_IS_CON_CS, offset3_is_con);
        setConstant(HAND_UNROLLING1_CS, hand_unrolling1);
        setConstant(HAND_UNROLLING2_CS, hand_unrolling2);
        setConstant(HAND_UNROLLING3_CS, hand_unrolling3);
    }

    public static void main(String[] args) {
        byte[] aB = new byte[RANGE_CON];
        byte[] bB = new byte[RANGE_CON];
        byte[] cB = new byte[RANGE_CON];
        short[] aS = new short[RANGE_CON];
        short[] bS = new short[RANGE_CON];
        short[] cS = new short[RANGE_CON];
        char[] aC = new char[RANGE_CON];
        char[] bC = new char[RANGE_CON];
        char[] cC = new char[RANGE_CON];
        int[] aI = new int[RANGE_CON];
        int[] bI = new int[RANGE_CON];
        int[] cI = new int[RANGE_CON];
        long[] aL = new long[RANGE_CON];
        long[] bL = new long[RANGE_CON];
        long[] cL = new long[RANGE_CON];
        float[] aF = new float[RANGE_CON];
        float[] bF = new float[RANGE_CON];
        float[] cF = new float[RANGE_CON];
        double[] aD = new double[RANGE_CON];
        double[] bD = new double[RANGE_CON];
        double[] cD = new double[RANGE_CON];
 
	for (int i = 1; i <= ITERATIONS; i++) {
            System.out.println("ITERATION " + i + " of " + ITERATIONS);
            setRandomConstants();
            // Have enough iterations to deoptimize and re-compile
            for (int j = 0; j < 20_000; j++) {
                testUUB(aB);
                testDDB(aB);
                testUDB(aB);
                testDUB(aB);

                testUUBH(aB);

                testUUBBB(aB, bB, cB);
                testUUBSI(aB, bS, cI);

                testUUBBBH(aB, bB, cB);
                testUUBCFH(aB, bC, cF);

                testMMSFD(aS, bF, cD);
            }
        }
        System.out.println("TEST PASSED");
    }

    // Test names:
    // test
    // {U: i goes up, D: i goes down, M: mixed}
    // {U: indexing goes up, D: indexing goes down, M: mixed}
    // BSCILFD (types used)

    // -------------------- BASIC SINGLE --------------------

    static void testUUB(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int offset = offset1_con_or_var();

        for (int i = init; i < limit; i += stride) {
            a[offset + i * scale]++;
        }
    }

    static void testDDB(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int offset = offset1_con_or_var();

        for (int i = limit; i > init; i -= stride) {
            a[offset + i * scale]++;
        }
    }

    static void testUDB(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int x = opposite_direction_offset1_con_or_var();
 
        for (int i = init; i < limit; i += stride) {
           a[x - i * scale]++;
        }
    }

    static void testDUB(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int x = opposite_direction_offset1_con_or_var();

        for (int i = limit; i > init; i -= stride) {
           a[x - i * scale]++;
        }
    }

    // -------------------- BASIC HAND UNROLL --------------------

    static void testUUBH(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int offset = offset1_con_or_var();

        // All if statements with constant h fold to true or false
        int h = hand_unrolling1_con();

        for (int i = init; i < limit; i += stride) {
            if (h >=  1) { a[offset + i * scale +  0]++; }
            if (h >=  2) { a[offset + i * scale +  1]++; }
            if (h >=  3) { a[offset + i * scale +  2]++; }
            if (h >=  4) { a[offset + i * scale +  3]++; }
            if (h >=  5) { a[offset + i * scale +  4]++; }
            if (h >=  6) { a[offset + i * scale +  5]++; }
            if (h >=  7) { a[offset + i * scale +  6]++; }
            if (h >=  8) { a[offset + i * scale +  7]++; }
            if (h >=  9) { a[offset + i * scale +  8]++; }
            if (h >= 10) { a[offset + i * scale +  9]++; }
            if (h >= 11) { a[offset + i * scale + 10]++; }
            if (h >= 12) { a[offset + i * scale + 11]++; }
            if (h >= 13) { a[offset + i * scale + 12]++; }
            if (h >= 14) { a[offset + i * scale + 13]++; }
            if (h >= 15) { a[offset + i * scale + 14]++; }
            if (h >= 16) { a[offset + i * scale + 15]++; }
        }
    }

    // -------------------- BASIC TRIPPLE --------------------

    static void testUUBBB(byte[] a, byte[] b, byte[] c) {
        int init    = init_con_or_var();
        int limit   = limit_con_or_var();
        int stride  = stride_con();
        int scale   = scale_con();
        int offset1 = offset1_con_or_var();
        int offset2 = offset2_con_or_var();
        int offset3 = offset3_con_or_var();

        for (int i = init; i < limit; i += stride) {
            a[offset1 + i * scale]++;
            b[offset2 + i * scale]++;
            c[offset3 + i * scale]++;
        }
    }

    static void testUUBSI(byte[] a, short[] b, int[] c) {
        int init    = init_con_or_var();
        int limit   = limit_con_or_var();
        int stride  = stride_con();
        int scale   = scale_con();
        int offset1 = offset1_con_or_var();
        int offset2 = offset2_con_or_var();
        int offset3 = offset3_con_or_var();

        for (int i = init; i < limit; i += stride) {
            a[offset1 + i * scale]++;
            b[offset2 + i * scale]++;
            c[offset3 + i * scale]++;
        }
    }

    // -------------------- HAND UNROLL TRIPPLE --------------------

    static void testUUBBBH(byte[] a, byte[] b, byte[] c) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int offset1 = offset1_con_or_var();
        int offset2 = offset2_con_or_var();
        int offset3 = offset3_con_or_var();

        int h1 = hand_unrolling1_con();
        int h2 = hand_unrolling2_con();
        int h3 = hand_unrolling3_con();

        for (int i = init; i < limit; i += stride) {
            if (h1 >=  1) { a[offset1 + i * scale +  0]++; }
            if (h1 >=  2) { a[offset1 + i * scale +  1]++; }
            if (h1 >=  3) { a[offset1 + i * scale +  2]++; }
            if (h1 >=  4) { a[offset1 + i * scale +  3]++; }
            if (h1 >=  5) { a[offset1 + i * scale +  4]++; }
            if (h1 >=  6) { a[offset1 + i * scale +  5]++; }
            if (h1 >=  7) { a[offset1 + i * scale +  6]++; }
            if (h1 >=  8) { a[offset1 + i * scale +  7]++; }
            if (h1 >=  9) { a[offset1 + i * scale +  8]++; }
            if (h1 >= 10) { a[offset1 + i * scale +  9]++; }
            if (h1 >= 11) { a[offset1 + i * scale + 10]++; }
            if (h1 >= 12) { a[offset1 + i * scale + 11]++; }
            if (h1 >= 13) { a[offset1 + i * scale + 12]++; }
            if (h1 >= 14) { a[offset1 + i * scale + 13]++; }
            if (h1 >= 15) { a[offset1 + i * scale + 14]++; }
            if (h1 >= 16) { a[offset1 + i * scale + 15]++; }

            if (h2 >=  1) { b[offset2 + i * scale +  0]++; }
            if (h2 >=  2) { b[offset2 + i * scale +  1]++; }
            if (h2 >=  3) { b[offset2 + i * scale +  2]++; }
            if (h2 >=  4) { b[offset2 + i * scale +  3]++; }
            if (h2 >=  5) { b[offset2 + i * scale +  4]++; }
            if (h2 >=  6) { b[offset2 + i * scale +  5]++; }
            if (h2 >=  7) { b[offset2 + i * scale +  6]++; }
            if (h2 >=  8) { b[offset2 + i * scale +  7]++; }
            if (h2 >=  9) { b[offset2 + i * scale +  8]++; }
            if (h2 >= 10) { b[offset2 + i * scale +  9]++; }
            if (h2 >= 11) { b[offset2 + i * scale + 10]++; }
            if (h2 >= 12) { b[offset2 + i * scale + 11]++; }
            if (h2 >= 13) { b[offset2 + i * scale + 12]++; }
            if (h2 >= 14) { b[offset2 + i * scale + 13]++; }
            if (h2 >= 15) { b[offset2 + i * scale + 14]++; }
            if (h2 >= 16) { b[offset2 + i * scale + 15]++; }

            if (h3 >=  1) { c[offset3 + i * scale +  0]++; }
            if (h3 >=  2) { c[offset3 + i * scale +  1]++; }
            if (h3 >=  3) { c[offset3 + i * scale +  2]++; }
            if (h3 >=  4) { c[offset3 + i * scale +  3]++; }
            if (h3 >=  5) { c[offset3 + i * scale +  4]++; }
            if (h3 >=  6) { c[offset3 + i * scale +  5]++; }
            if (h3 >=  7) { c[offset3 + i * scale +  6]++; }
            if (h3 >=  8) { c[offset3 + i * scale +  7]++; }
            if (h3 >=  9) { c[offset3 + i * scale +  8]++; }
            if (h3 >= 10) { c[offset3 + i * scale +  9]++; }
            if (h3 >= 11) { c[offset3 + i * scale + 10]++; }
            if (h3 >= 12) { c[offset3 + i * scale + 11]++; }
            if (h3 >= 13) { c[offset3 + i * scale + 12]++; }
            if (h3 >= 14) { c[offset3 + i * scale + 13]++; }
            if (h3 >= 15) { c[offset3 + i * scale + 14]++; }
            if (h3 >= 16) { c[offset3 + i * scale + 15]++; }
        }
    }

    static void testUUBCFH(byte[] a, char[] b, float[] c) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int offset1 = offset1_con_or_var();
        int offset2 = offset2_con_or_var();
        int offset3 = offset3_con_or_var();

        int h1 = hand_unrolling1_con();
        int h2 = hand_unrolling2_con();
        int h3 = hand_unrolling3_con();

        for (int i = init; i < limit; i += stride) {
            if (h1 >=  1) { a[offset1 + i * scale +  0]++; }
            if (h1 >=  2) { a[offset1 + i * scale +  1]++; }
            if (h1 >=  3) { a[offset1 + i * scale +  2]++; }
            if (h1 >=  4) { a[offset1 + i * scale +  3]++; }
            if (h1 >=  5) { a[offset1 + i * scale +  4]++; }
            if (h1 >=  6) { a[offset1 + i * scale +  5]++; }
            if (h1 >=  7) { a[offset1 + i * scale +  6]++; }
            if (h1 >=  8) { a[offset1 + i * scale +  7]++; }
            if (h1 >=  9) { a[offset1 + i * scale +  8]++; }
            if (h1 >= 10) { a[offset1 + i * scale +  9]++; }
            if (h1 >= 11) { a[offset1 + i * scale + 10]++; }
            if (h1 >= 12) { a[offset1 + i * scale + 11]++; }
            if (h1 >= 13) { a[offset1 + i * scale + 12]++; }
            if (h1 >= 14) { a[offset1 + i * scale + 13]++; }
            if (h1 >= 15) { a[offset1 + i * scale + 14]++; }
            if (h1 >= 16) { a[offset1 + i * scale + 15]++; }

            if (h2 >=  1) { b[offset2 + i * scale +  0]++; }
            if (h2 >=  2) { b[offset2 + i * scale +  1]++; }
            if (h2 >=  3) { b[offset2 + i * scale +  2]++; }
            if (h2 >=  4) { b[offset2 + i * scale +  3]++; }
            if (h2 >=  5) { b[offset2 + i * scale +  4]++; }
            if (h2 >=  6) { b[offset2 + i * scale +  5]++; }
            if (h2 >=  7) { b[offset2 + i * scale +  6]++; }
            if (h2 >=  8) { b[offset2 + i * scale +  7]++; }
            if (h2 >=  9) { b[offset2 + i * scale +  8]++; }
            if (h2 >= 10) { b[offset2 + i * scale +  9]++; }
            if (h2 >= 11) { b[offset2 + i * scale + 10]++; }
            if (h2 >= 12) { b[offset2 + i * scale + 11]++; }
            if (h2 >= 13) { b[offset2 + i * scale + 12]++; }
            if (h2 >= 14) { b[offset2 + i * scale + 13]++; }
            if (h2 >= 15) { b[offset2 + i * scale + 14]++; }
            if (h2 >= 16) { b[offset2 + i * scale + 15]++; }

            if (h3 >=  1) { c[offset3 + i * scale +  0]++; }
            if (h3 >=  2) { c[offset3 + i * scale +  1]++; }
            if (h3 >=  3) { c[offset3 + i * scale +  2]++; }
            if (h3 >=  4) { c[offset3 + i * scale +  3]++; }
            if (h3 >=  5) { c[offset3 + i * scale +  4]++; }
            if (h3 >=  6) { c[offset3 + i * scale +  5]++; }
            if (h3 >=  7) { c[offset3 + i * scale +  6]++; }
            if (h3 >=  8) { c[offset3 + i * scale +  7]++; }
            if (h3 >=  9) { c[offset3 + i * scale +  8]++; }
            if (h3 >= 10) { c[offset3 + i * scale +  9]++; }
            if (h3 >= 11) { c[offset3 + i * scale + 10]++; }
            if (h3 >= 12) { c[offset3 + i * scale + 11]++; }
            if (h3 >= 13) { c[offset3 + i * scale + 12]++; }
            if (h3 >= 14) { c[offset3 + i * scale + 13]++; }
            if (h3 >= 15) { c[offset3 + i * scale + 14]++; }
            if (h3 >= 16) { c[offset3 + i * scale + 15]++; }
        }
    }

    // -------------------- MIXED DIRECTION TRIPPLE --------------------

    static void testMMSFD(short[] a, float[] b, double[] c) {
        int init    = init_con_or_var();
        int limit   = limit_con_or_var();
        int stride  = stride_con();
        int scale   = scale_con();
        int offset1 = offset1_con_or_var();
        int offset2 = opposite_direction_offset2_con_or_var();
        int offset3 = offset3_con_or_var();

        for (int i = init; i < limit; i += stride) {
            a[offset1 + i * scale]++;
            b[offset2 - i * scale]++;
            c[offset3 + i * scale]++;
        }
    }

}
