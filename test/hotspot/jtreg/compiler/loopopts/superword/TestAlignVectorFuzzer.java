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

/*
 * @test id=Vanilla
 * @bug 8253191
 * @summary Fuzzing loops with different (random) init, limit, stride, scale etc. Do not force alignment.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.compiler2.enabled
 * @key randomness
 * @run main/bootclasspath/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                                 -XX:LoopUnrollLimit=250
 *                                 -XX:CompileCommand=printcompilation,compiler.loopopts.superword.TestAlignVectorFuzzer::*
 *                                 compiler.loopopts.superword.TestAlignVectorFuzzer
 */

/*
 * @test id=VerifyAlignVector
 * @bug 8253191
 * @summary Fuzzing loops with different (random) init, limit, stride, scale etc. Verify AlignVector.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.compiler2.enabled
 * @key randomness
 * @run main/bootclasspath/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                                 -XX:+AlignVector -XX:+VerifyAlignVector
 *                                 -XX:LoopUnrollLimit=250
 *                                 -XX:CompileCommand=printcompilation,compiler.loopopts.superword.TestAlignVectorFuzzer::*
 *                                 compiler.loopopts.superword.TestAlignVectorFuzzer
 */

/*
 * @test id=VerifyAlignVector-Align16
 * @bug 8253191
 * @summary Fuzzing loops with different (random) init, limit, stride, scale etc. Verify AlignVector.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.compiler2.enabled
 * @requires vm.bits == 64
 * @key randomness
 * @run main/bootclasspath/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                                 -XX:+AlignVector -XX:+VerifyAlignVector
 *                                 -XX:LoopUnrollLimit=250
 *                                 -XX:CompileCommand=printcompilation,compiler.loopopts.superword.TestAlignVectorFuzzer::*
 *                                 -XX:ObjectAlignmentInBytes=16
 *                                 compiler.loopopts.superword.TestAlignVectorFuzzer
 */

/*
 * @test id=VerifyAlignVector-NoTieredCompilation-Xbatch
 * @bug 8253191
 * @summary Fuzzing loops with different (random) init, limit, stride, scale etc. Verify AlignVector.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.compiler2.enabled
 * @key randomness
 * @run main/bootclasspath/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                                 -XX:+AlignVector -XX:+VerifyAlignVector
 *                                 -XX:LoopUnrollLimit=250
 *                                 -XX:CompileCommand=printcompilation,compiler.loopopts.superword.TestAlignVectorFuzzer::*
 *                                 -XX:-TieredCompilation -Xbatch
 *                                 compiler.loopopts.superword.TestAlignVectorFuzzer
 */

package compiler.loopopts.superword;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.HashMap;
import java.lang.invoke.*;
import java.util.Random;
import jdk.test.lib.Utils;
import jdk.internal.misc.Unsafe;

public class TestAlignVectorFuzzer {
    static final int ITERATIONS_MAX = 5; // time allowance may lead to fewer iterations
    static final int RANGE_CON = 1024 * 8;
    static int ZERO = 0;

    private static final Random random = Utils.getRandomInstance();
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    interface TestFunction {
        Object[] run();
    }

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
            init += ZERO; // LoadI
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
            limit -= ZERO; // LoadI
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
            offset += ZERO; // LoadI
        }
        return offset;
    }

    static int offset2_con_or_var() {
        int offset = offset2_con();
        if (!offset2_is_con()) { // branch constant folds to true or false
            offset += ZERO; // LoadI
        }
        return offset;
    }

    static int offset3_con_or_var() {
        int offset = offset3_con();
        if (!offset3_is_con()) { // branch constant folds to true or false
            offset += ZERO; // LoadI
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

//      Overwrite the fuzzed values below to reproduce a specific failure:
//
//        init = 1;
//        limit = init + 3000;
//        offset1 = 0;
//        offset2 = 0;
//        offset3 = 32 - 2*init;
//        stride =  1;
//        scale =   2;
//        hand_unrolling1 = 0;
//        hand_unrolling2 = 0;
//        hand_unrolling3 = 4;
//
//        init_is_con    = true;
//        limit_is_con   = true;
//        offset1_is_con = true;
//        offset2_is_con = true;
//        offset3_is_con = true;

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
        byte[] aB = generateB();
        byte[] bB = generateB();
        byte[] cB = generateB();
        short[] aS = generateS();
        short[] bS = generateS();
        short[] cS = generateS();
        char[] aC = generateC();
        char[] bC = generateC();
        char[] cC = generateC();
        int[] aI = generateI();
        int[] bI = generateI();
        int[] cI = generateI();
        long[] aL = generateL();
        long[] bL = generateL();
        long[] cL = generateL();
        float[] aF = generateF();
        float[] bF = generateF();
        float[] cF = generateF();
        double[] aD = generateD();
        double[] bD = generateD();
        double[] cD = generateD();

        // Add all tests to list
        Map<String,TestFunction> tests = new HashMap<String,TestFunction>();
        tests.put("testUUB", () -> { return testUUB(aB.clone()); });
        tests.put("testDDB", () -> { return testDDB(aB.clone()); });
        tests.put("testUDB", () -> { return testUDB(aB.clone()); });
        tests.put("testDUB", () -> { return testDUB(aB.clone()); });

        tests.put("testUUBH", () -> { return testUUBH(aB.clone()); });

        tests.put("testUUBBB", () -> { return testUUBBB(aB.clone(), bB.clone(), cB.clone()); });
        tests.put("testUUBSI", () -> { return testUUBSI(aB.clone(), bS.clone(), cI.clone()); });

        tests.put("testUUBBBH", () -> { return testUUBBBH(aB.clone(), bB.clone(), cB.clone()); });

        tests.put("testUUBCFH", () -> { return testUUBCFH(aB.clone(), bC.clone(), cF.clone()); });
        tests.put("testDDBCFH", () -> { return testDDBCFH(aB.clone(), bC.clone(), cF.clone()); });
        tests.put("testUDBCFH", () -> { return testUDBCFH(aB.clone(), bC.clone(), cF.clone()); });
        tests.put("testDUBCFH", () -> { return testDUBCFH(aB.clone(), bC.clone(), cF.clone()); });

        tests.put("testMMSFD", () -> { return testMMSFD(aS.clone(), bF.clone(), cD.clone()); });

        tests.put("testUU_unsafe_BasI", () -> { return testUU_unsafe_BasI(aB.clone()); });
        tests.put("testUU_unsafe_BasIH", () -> { return testUU_unsafe_BasIH(aB.clone(), bB.clone(), cB.clone()); });


        // Only run for 90% of the time, and subtract some margin. This ensures the shutdown has sufficient time,
        // even for very slow runs.
        long test_time_allowance = System.currentTimeMillis() +
                                   (long)(Utils.adjustTimeout(Utils.DEFAULT_TEST_TIMEOUT) * 0.9) -
                                   20_000;
        long test_hard_timeout = System.currentTimeMillis() +
                                Utils.adjustTimeout(Utils.DEFAULT_TEST_TIMEOUT);

        for (int i = 1; i <= ITERATIONS_MAX; i++) {
            setRandomConstants();
            for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
                String name = entry.getKey();
                TestFunction test = entry.getValue();
                long allowance = test_time_allowance - System.currentTimeMillis();
                long until_timeout = test_hard_timeout - System.currentTimeMillis();
                System.out.println("ITERATION " + i + " of " + ITERATIONS_MAX + ". Test " + name +
                                   ", time allowance: " + allowance + ", until timeout: " + until_timeout);

                // Compute gold value, probably deopt first if constants have changed.
                Object[] gold = test.run();

                // Have enough iterations to (re)compile
                for (int j = 0; j < 10_000; j++) {
                    Object[] result = test.run();
                    verify(name, gold, result);
                }

                if (System.currentTimeMillis() > test_time_allowance) {
                    allowance = test_time_allowance - System.currentTimeMillis();
                    until_timeout = test_hard_timeout - System.currentTimeMillis();
                    System.out.println("TEST PASSED: hit maximal time allownance during iteration " + i +
                                       ", time allowance: " + allowance + ", until timeout: " + until_timeout);
                    return;
                }
            }
        }
        long allowance = test_time_allowance - System.currentTimeMillis();
        long until_timeout = test_hard_timeout - System.currentTimeMillis();
        System.out.println("TEST PASSED, time allowance: " + allowance + ", until timeout: " + until_timeout);
    }

    // Test names:
    // test
    // {U: i goes up, D: i goes down, M: mixed}
    // {U: indexing goes up, D: indexing goes down, M: mixed}
    // BSCILFD (types used)

    // -------------------- BASIC SINGLE --------------------

    static Object[] testUUB(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int offset = offset1_con_or_var();

        for (int i = init; i < limit; i += stride) {
            a[offset + i * scale]++;
        }
        return new Object[]{ a };
    }

    static Object[] testDDB(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int offset = offset1_con_or_var();

        for (int i = limit; i > init; i -= stride) {
            a[offset + i * scale]++;
        }
        return new Object[]{ a };
    }

    static Object[] testUDB(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int x = opposite_direction_offset1_con_or_var();

        for (int i = init; i < limit; i += stride) {
           a[x - i * scale]++;
        }
        return new Object[]{ a };
    }

    static Object[] testDUB(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int x = opposite_direction_offset1_con_or_var();

        for (int i = limit; i > init; i -= stride) {
           a[x - i * scale]++;
        }
        return new Object[]{ a };
    }

    // -------------------- BASIC HAND UNROLL --------------------

    static Object[] testUUBH(byte[] a) {
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
        return new Object[]{ a };
    }

    // -------------------- BASIC TRIPPLE --------------------

    static Object[] testUUBBB(byte[] a, byte[] b, byte[] c) {
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
        return new Object[]{ a, b, c };
    }

    static Object[] testUUBSI(byte[] a, short[] b, int[] c) {
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
        return new Object[]{ a, b, c };
    }

    // -------------------- HAND UNROLL TRIPPLE --------------------

    static Object[] testUUBBBH(byte[] a, byte[] b, byte[] c) {
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
        return new Object[]{ a, b, c };
    }

    static Object[] testUUBCFH(byte[] a, char[] b, float[] c) {
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
        return new Object[]{ a, b, c };
    }

    static Object[] testDDBCFH(byte[] a, char[] b, float[] c) {
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

        for (int i = limit; i > init; i -= stride) {
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
        return new Object[]{ a, b, c };
    }

    static Object[] testUDBCFH(byte[] a, char[] b, float[] c) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int x1 = opposite_direction_offset1_con_or_var();
        int x2 = opposite_direction_offset2_con_or_var();
        int x3 = opposite_direction_offset3_con_or_var();

        int h1 = hand_unrolling1_con();
        int h2 = hand_unrolling2_con();
        int h3 = hand_unrolling3_con();

        for (int i = init; i < limit; i += stride) {
            if (h1 >=  1) { a[x1 - i * scale +  0]++; }
            if (h1 >=  2) { a[x1 - i * scale +  1]++; }
            if (h1 >=  3) { a[x1 - i * scale +  2]++; }
            if (h1 >=  4) { a[x1 - i * scale +  3]++; }
            if (h1 >=  5) { a[x1 - i * scale +  4]++; }
            if (h1 >=  6) { a[x1 - i * scale +  5]++; }
            if (h1 >=  7) { a[x1 - i * scale +  6]++; }
            if (h1 >=  8) { a[x1 - i * scale +  7]++; }
            if (h1 >=  9) { a[x1 - i * scale +  8]++; }
            if (h1 >= 10) { a[x1 - i * scale +  9]++; }
            if (h1 >= 11) { a[x1 - i * scale + 10]++; }
            if (h1 >= 12) { a[x1 - i * scale + 11]++; }
            if (h1 >= 13) { a[x1 - i * scale + 12]++; }
            if (h1 >= 14) { a[x1 - i * scale + 13]++; }
            if (h1 >= 15) { a[x1 - i * scale + 14]++; }
            if (h1 >= 16) { a[x1 - i * scale + 15]++; }

            if (h2 >=  1) { b[x2 - i * scale +  0]++; }
            if (h2 >=  2) { b[x2 - i * scale +  1]++; }
            if (h2 >=  3) { b[x2 - i * scale +  2]++; }
            if (h2 >=  4) { b[x2 - i * scale +  3]++; }
            if (h2 >=  5) { b[x2 - i * scale +  4]++; }
            if (h2 >=  6) { b[x2 - i * scale +  5]++; }
            if (h2 >=  7) { b[x2 - i * scale +  6]++; }
            if (h2 >=  8) { b[x2 - i * scale +  7]++; }
            if (h2 >=  9) { b[x2 - i * scale +  8]++; }
            if (h2 >= 10) { b[x2 - i * scale +  9]++; }
            if (h2 >= 11) { b[x2 - i * scale + 10]++; }
            if (h2 >= 12) { b[x2 - i * scale + 11]++; }
            if (h2 >= 13) { b[x2 - i * scale + 12]++; }
            if (h2 >= 14) { b[x2 - i * scale + 13]++; }
            if (h2 >= 15) { b[x2 - i * scale + 14]++; }
            if (h2 >= 16) { b[x2 - i * scale + 15]++; }

            if (h3 >=  1) { c[x3 - i * scale +  0]++; }
            if (h3 >=  2) { c[x3 - i * scale +  1]++; }
            if (h3 >=  3) { c[x3 - i * scale +  2]++; }
            if (h3 >=  4) { c[x3 - i * scale +  3]++; }
            if (h3 >=  5) { c[x3 - i * scale +  4]++; }
            if (h3 >=  6) { c[x3 - i * scale +  5]++; }
            if (h3 >=  7) { c[x3 - i * scale +  6]++; }
            if (h3 >=  8) { c[x3 - i * scale +  7]++; }
            if (h3 >=  9) { c[x3 - i * scale +  8]++; }
            if (h3 >= 10) { c[x3 - i * scale +  9]++; }
            if (h3 >= 11) { c[x3 - i * scale + 10]++; }
            if (h3 >= 12) { c[x3 - i * scale + 11]++; }
            if (h3 >= 13) { c[x3 - i * scale + 12]++; }
            if (h3 >= 14) { c[x3 - i * scale + 13]++; }
            if (h3 >= 15) { c[x3 - i * scale + 14]++; }
            if (h3 >= 16) { c[x3 - i * scale + 15]++; }
        }
        return new Object[]{ a, b, c };
    }

    static Object[] testDUBCFH(byte[] a, char[] b, float[] c) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int x1 = opposite_direction_offset1_con_or_var();
        int x2 = opposite_direction_offset2_con_or_var();
        int x3 = opposite_direction_offset3_con_or_var();

        int h1 = hand_unrolling1_con();
        int h2 = hand_unrolling2_con();
        int h3 = hand_unrolling3_con();

        for (int i = limit; i > init; i -= stride) {
            if (h1 >=  1) { a[x1 - i * scale +  0]++; }
            if (h1 >=  2) { a[x1 - i * scale +  1]++; }
            if (h1 >=  3) { a[x1 - i * scale +  2]++; }
            if (h1 >=  4) { a[x1 - i * scale +  3]++; }
            if (h1 >=  5) { a[x1 - i * scale +  4]++; }
            if (h1 >=  6) { a[x1 - i * scale +  5]++; }
            if (h1 >=  7) { a[x1 - i * scale +  6]++; }
            if (h1 >=  8) { a[x1 - i * scale +  7]++; }
            if (h1 >=  9) { a[x1 - i * scale +  8]++; }
            if (h1 >= 10) { a[x1 - i * scale +  9]++; }
            if (h1 >= 11) { a[x1 - i * scale + 10]++; }
            if (h1 >= 12) { a[x1 - i * scale + 11]++; }
            if (h1 >= 13) { a[x1 - i * scale + 12]++; }
            if (h1 >= 14) { a[x1 - i * scale + 13]++; }
            if (h1 >= 15) { a[x1 - i * scale + 14]++; }
            if (h1 >= 16) { a[x1 - i * scale + 15]++; }

            if (h2 >=  1) { b[x2 - i * scale +  0]++; }
            if (h2 >=  2) { b[x2 - i * scale +  1]++; }
            if (h2 >=  3) { b[x2 - i * scale +  2]++; }
            if (h2 >=  4) { b[x2 - i * scale +  3]++; }
            if (h2 >=  5) { b[x2 - i * scale +  4]++; }
            if (h2 >=  6) { b[x2 - i * scale +  5]++; }
            if (h2 >=  7) { b[x2 - i * scale +  6]++; }
            if (h2 >=  8) { b[x2 - i * scale +  7]++; }
            if (h2 >=  9) { b[x2 - i * scale +  8]++; }
            if (h2 >= 10) { b[x2 - i * scale +  9]++; }
            if (h2 >= 11) { b[x2 - i * scale + 10]++; }
            if (h2 >= 12) { b[x2 - i * scale + 11]++; }
            if (h2 >= 13) { b[x2 - i * scale + 12]++; }
            if (h2 >= 14) { b[x2 - i * scale + 13]++; }
            if (h2 >= 15) { b[x2 - i * scale + 14]++; }
            if (h2 >= 16) { b[x2 - i * scale + 15]++; }

            if (h3 >=  1) { c[x3 - i * scale +  0]++; }
            if (h3 >=  2) { c[x3 - i * scale +  1]++; }
            if (h3 >=  3) { c[x3 - i * scale +  2]++; }
            if (h3 >=  4) { c[x3 - i * scale +  3]++; }
            if (h3 >=  5) { c[x3 - i * scale +  4]++; }
            if (h3 >=  6) { c[x3 - i * scale +  5]++; }
            if (h3 >=  7) { c[x3 - i * scale +  6]++; }
            if (h3 >=  8) { c[x3 - i * scale +  7]++; }
            if (h3 >=  9) { c[x3 - i * scale +  8]++; }
            if (h3 >= 10) { c[x3 - i * scale +  9]++; }
            if (h3 >= 11) { c[x3 - i * scale + 10]++; }
            if (h3 >= 12) { c[x3 - i * scale + 11]++; }
            if (h3 >= 13) { c[x3 - i * scale + 12]++; }
            if (h3 >= 14) { c[x3 - i * scale + 13]++; }
            if (h3 >= 15) { c[x3 - i * scale + 14]++; }
            if (h3 >= 16) { c[x3 - i * scale + 15]++; }
        }
        return new Object[]{ a, b, c };
    }

    // -------------------- MIXED DIRECTION TRIPPLE --------------------

    static Object[] testMMSFD(short[] a, float[] b, double[] c) {
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
        return new Object[]{ a, b, c };
    }

    // -------------------- UNSAFE --------------------

    static Object[] testUU_unsafe_BasI(byte[] a) {
        int init   = init_con_or_var();
        int limit  = limit_con_or_var();
        int stride = stride_con();
        int scale  = scale_con();
        int offset = offset1_con_or_var();

        for (int i = init; i < limit; i += stride) {
            int adr = UNSAFE.ARRAY_BYTE_BASE_OFFSET + offset + i * scale;
            int v = UNSAFE.getIntUnaligned(a, adr);
            UNSAFE.putIntUnaligned(a, adr, v + 1);
        }
        return new Object[]{ a };
    }

    static Object[] testUU_unsafe_BasIH(byte[] a, byte[] b, byte[] c) {
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
            int adr1 = UNSAFE.ARRAY_BYTE_BASE_OFFSET + offset1 + i * scale;
            int adr2 = UNSAFE.ARRAY_BYTE_BASE_OFFSET + offset2 + i * scale;
            int adr3 = UNSAFE.ARRAY_BYTE_BASE_OFFSET + offset3 + i * scale;

            if (h1 >=  1) { UNSAFE.putIntUnaligned(a, adr1 +  0*4, UNSAFE.getIntUnaligned(a, adr1 +  0*4) + 1); }
            if (h1 >=  2) { UNSAFE.putIntUnaligned(a, adr1 +  1*4, UNSAFE.getIntUnaligned(a, adr1 +  1*4) + 1); }
            if (h1 >=  3) { UNSAFE.putIntUnaligned(a, adr1 +  2*4, UNSAFE.getIntUnaligned(a, adr1 +  2*4) + 1); }
            if (h1 >=  4) { UNSAFE.putIntUnaligned(a, adr1 +  3*4, UNSAFE.getIntUnaligned(a, adr1 +  3*4) + 1); }
            if (h1 >=  5) { UNSAFE.putIntUnaligned(a, adr1 +  4*4, UNSAFE.getIntUnaligned(a, adr1 +  4*4) + 1); }
            if (h1 >=  6) { UNSAFE.putIntUnaligned(a, adr1 +  5*4, UNSAFE.getIntUnaligned(a, adr1 +  5*4) + 1); }
            if (h1 >=  7) { UNSAFE.putIntUnaligned(a, adr1 +  6*4, UNSAFE.getIntUnaligned(a, adr1 +  6*4) + 1); }
            if (h1 >=  8) { UNSAFE.putIntUnaligned(a, adr1 +  7*4, UNSAFE.getIntUnaligned(a, adr1 +  7*4) + 1); }
            if (h1 >=  9) { UNSAFE.putIntUnaligned(a, adr1 +  8*4, UNSAFE.getIntUnaligned(a, adr1 +  8*4) + 1); }
            if (h1 >= 10) { UNSAFE.putIntUnaligned(a, adr1 +  9*4, UNSAFE.getIntUnaligned(a, adr1 +  9*4) + 1); }
            if (h1 >= 11) { UNSAFE.putIntUnaligned(a, adr1 + 10*4, UNSAFE.getIntUnaligned(a, adr1 + 10*4) + 1); }
            if (h1 >= 12) { UNSAFE.putIntUnaligned(a, adr1 + 11*4, UNSAFE.getIntUnaligned(a, adr1 + 11*4) + 1); }
            if (h1 >= 13) { UNSAFE.putIntUnaligned(a, adr1 + 12*4, UNSAFE.getIntUnaligned(a, adr1 + 12*4) + 1); }
            if (h1 >= 14) { UNSAFE.putIntUnaligned(a, adr1 + 13*4, UNSAFE.getIntUnaligned(a, adr1 + 13*4) + 1); }
            if (h1 >= 15) { UNSAFE.putIntUnaligned(a, adr1 + 14*4, UNSAFE.getIntUnaligned(a, adr1 + 14*4) + 1); }
            if (h1 >= 16) { UNSAFE.putIntUnaligned(a, adr1 + 15*4, UNSAFE.getIntUnaligned(a, adr1 + 15*4) + 1); }

            if (h2 >=  1) { UNSAFE.putIntUnaligned(b, adr2 +  0*4, UNSAFE.getIntUnaligned(b, adr2 +  0*4) + 1); }
            if (h2 >=  2) { UNSAFE.putIntUnaligned(b, adr2 +  1*4, UNSAFE.getIntUnaligned(b, adr2 +  1*4) + 1); }
            if (h2 >=  3) { UNSAFE.putIntUnaligned(b, adr2 +  2*4, UNSAFE.getIntUnaligned(b, adr2 +  2*4) + 1); }
            if (h2 >=  4) { UNSAFE.putIntUnaligned(b, adr2 +  3*4, UNSAFE.getIntUnaligned(b, adr2 +  3*4) + 1); }
            if (h2 >=  5) { UNSAFE.putIntUnaligned(b, adr2 +  4*4, UNSAFE.getIntUnaligned(b, adr2 +  4*4) + 1); }
            if (h2 >=  6) { UNSAFE.putIntUnaligned(b, adr2 +  5*4, UNSAFE.getIntUnaligned(b, adr2 +  5*4) + 1); }
            if (h2 >=  7) { UNSAFE.putIntUnaligned(b, adr2 +  6*4, UNSAFE.getIntUnaligned(b, adr2 +  6*4) + 1); }
            if (h2 >=  8) { UNSAFE.putIntUnaligned(b, adr2 +  7*4, UNSAFE.getIntUnaligned(b, adr2 +  7*4) + 1); }
            if (h2 >=  9) { UNSAFE.putIntUnaligned(b, adr2 +  8*4, UNSAFE.getIntUnaligned(b, adr2 +  8*4) + 1); }
            if (h2 >= 10) { UNSAFE.putIntUnaligned(b, adr2 +  9*4, UNSAFE.getIntUnaligned(b, adr2 +  9*4) + 1); }
            if (h2 >= 11) { UNSAFE.putIntUnaligned(b, adr2 + 10*4, UNSAFE.getIntUnaligned(b, adr2 + 10*4) + 1); }
            if (h2 >= 12) { UNSAFE.putIntUnaligned(b, adr2 + 11*4, UNSAFE.getIntUnaligned(b, adr2 + 11*4) + 1); }
            if (h2 >= 13) { UNSAFE.putIntUnaligned(b, adr2 + 12*4, UNSAFE.getIntUnaligned(b, adr2 + 12*4) + 1); }
            if (h2 >= 14) { UNSAFE.putIntUnaligned(b, adr2 + 13*4, UNSAFE.getIntUnaligned(b, adr2 + 13*4) + 1); }
            if (h2 >= 15) { UNSAFE.putIntUnaligned(b, adr2 + 14*4, UNSAFE.getIntUnaligned(b, adr2 + 14*4) + 1); }
            if (h2 >= 16) { UNSAFE.putIntUnaligned(b, adr2 + 15*4, UNSAFE.getIntUnaligned(b, adr2 + 15*4) + 1); }

            if (h3 >=  1) { UNSAFE.putIntUnaligned(c, adr3 +  0*4, UNSAFE.getIntUnaligned(c, adr3 +  0*4) + 1); }
            if (h3 >=  2) { UNSAFE.putIntUnaligned(c, adr3 +  1*4, UNSAFE.getIntUnaligned(c, adr3 +  1*4) + 1); }
            if (h3 >=  3) { UNSAFE.putIntUnaligned(c, adr3 +  2*4, UNSAFE.getIntUnaligned(c, adr3 +  2*4) + 1); }
            if (h3 >=  4) { UNSAFE.putIntUnaligned(c, adr3 +  3*4, UNSAFE.getIntUnaligned(c, adr3 +  3*4) + 1); }
            if (h3 >=  5) { UNSAFE.putIntUnaligned(c, adr3 +  4*4, UNSAFE.getIntUnaligned(c, adr3 +  4*4) + 1); }
            if (h3 >=  6) { UNSAFE.putIntUnaligned(c, adr3 +  5*4, UNSAFE.getIntUnaligned(c, adr3 +  5*4) + 1); }
            if (h3 >=  7) { UNSAFE.putIntUnaligned(c, adr3 +  6*4, UNSAFE.getIntUnaligned(c, adr3 +  6*4) + 1); }
            if (h3 >=  8) { UNSAFE.putIntUnaligned(c, adr3 +  7*4, UNSAFE.getIntUnaligned(c, adr3 +  7*4) + 1); }
            if (h3 >=  9) { UNSAFE.putIntUnaligned(c, adr3 +  8*4, UNSAFE.getIntUnaligned(c, adr3 +  8*4) + 1); }
            if (h3 >= 10) { UNSAFE.putIntUnaligned(c, adr3 +  9*4, UNSAFE.getIntUnaligned(c, adr3 +  9*4) + 1); }
            if (h3 >= 11) { UNSAFE.putIntUnaligned(c, adr3 + 10*4, UNSAFE.getIntUnaligned(c, adr3 + 10*4) + 1); }
            if (h3 >= 12) { UNSAFE.putIntUnaligned(c, adr3 + 11*4, UNSAFE.getIntUnaligned(c, adr3 + 11*4) + 1); }
            if (h3 >= 13) { UNSAFE.putIntUnaligned(c, adr3 + 12*4, UNSAFE.getIntUnaligned(c, adr3 + 12*4) + 1); }
            if (h3 >= 14) { UNSAFE.putIntUnaligned(c, adr3 + 13*4, UNSAFE.getIntUnaligned(c, adr3 + 13*4) + 1); }
            if (h3 >= 15) { UNSAFE.putIntUnaligned(c, adr3 + 14*4, UNSAFE.getIntUnaligned(c, adr3 + 14*4) + 1); }
            if (h3 >= 16) { UNSAFE.putIntUnaligned(c, adr3 + 15*4, UNSAFE.getIntUnaligned(c, adr3 + 15*4) + 1); }
        }
        return new Object[]{ a, b, c };
    }

    static byte[] generateB() {
        byte[] a = new byte[RANGE_CON];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)random.nextInt();
        }
        return a;
    }

    static char[] generateC() {
        char[] a = new char[RANGE_CON];
        for (int i = 0; i < a.length; i++) {
            a[i] = (char)random.nextInt();
        }
        return a;
    }

    static short[] generateS() {
        short[] a = new short[RANGE_CON];
        for (int i = 0; i < a.length; i++) {
            a[i] = (short)random.nextInt();
        }
        return a;
    }

    static int[] generateI() {
        int[] a = new int[RANGE_CON];
        for (int i = 0; i < a.length; i++) {
            a[i] = random.nextInt();
        }
        return a;
    }

    static long[] generateL() {
        long[] a = new long[RANGE_CON];
        for (int i = 0; i < a.length; i++) {
            a[i] = random.nextLong();
        }
        return a;
    }

    static float[] generateF() {
        float[] a = new float[RANGE_CON];
        for (int i = 0; i < a.length; i++) {
            a[i] = Float.intBitsToFloat(random.nextInt());
        }
        return a;
    }

    static double[] generateD() {
        double[] a = new double[RANGE_CON];
        for (int i = 0; i < a.length; i++) {
            a[i] = Double.longBitsToDouble(random.nextLong());
        }
        return a;
    }

    static void verify(String name, Object[] gold, Object[] result) {
        if (gold.length != result.length) {
            throw new RuntimeException("verify " + name + ": not the same number of outputs: gold.length = " +
                                       gold.length + ", result.length = " + result.length);
        }
        for (int i = 0; i < gold.length; i++) {
            Object g = gold[i];
            Object r = result[i];
            if (g.getClass() != r.getClass() || !g.getClass().isArray() || !r.getClass().isArray()) {
                throw new RuntimeException("verify " + name + ": must both be array of same type:" +
                                           " gold[" + i + "].getClass() = " + g.getClass().getSimpleName() +
                                           " result[" + i + "].getClass() = " + r.getClass().getSimpleName());
            }
            if (g == r) {
                throw new RuntimeException("verify " + name + ": should be two separate arrays (with identical content):" +
                                           " gold[" + i + "] == result[" + i + "]");
            }
            if (Array.getLength(g) != Array.getLength(r)) {
                    throw new RuntimeException("verify " + name + ": arrays must have same length:" +
                                           " gold[" + i + "].length = " + Array.getLength(g) +
                                           " result[" + i + "].length = " + Array.getLength(r));
            }
            Class c = g.getClass().getComponentType();
            if (c == byte.class) {
                verifyB(name, i, (byte[])g, (byte[])r);
            } else if (c == char.class) {
                verifyC(name, i, (char[])g, (char[])r);
            } else if (c == short.class) {
                verifyS(name, i, (short[])g, (short[])r);
            } else if (c == int.class) {
                verifyI(name, i, (int[])g, (int[])r);
            } else if (c == long.class) {
                verifyL(name, i, (long[])g, (long[])r);
            } else if (c == float.class) {
                verifyF(name, i, (float[])g, (float[])r);
            } else if (c == double.class) {
                verifyD(name, i, (double[])g, (double[])r);
            } else {
                throw new RuntimeException("verify " + name + ": array type not supported for verify:" +
                                       " gold[" + i + "].getClass() = " + g.getClass().getSimpleName() +
                                       " result[" + i + "].getClass() = " + r.getClass().getSimpleName());
            }
        }
    }

    static void verifyB(String name, int i, byte[] g, byte[] r) {
        for (int j = 0; j < g.length; j++) {
            if (g[j] != r[j]) {
                throw new RuntimeException("verifyB " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    static void verifyC(String name, int i, char[] g, char[] r) {
        for (int j = 0; j < g.length; j++) {
            if (g[j] != r[j]) {
                throw new RuntimeException("verifyC " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    static void verifyS(String name, int i, short[] g, short[] r) {
        for (int j = 0; j < g.length; j++) {
            if (g[j] != r[j]) {
                throw new RuntimeException("verifyS " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    static void verifyI(String name, int i, int[] g, int[] r) {
        for (int j = 0; j < g.length; j++) {
            if (g[j] != r[j]) {
                throw new RuntimeException("verifyI " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    static void verifyL(String name, int i, long[] g, long[] r) {
        for (int j = 0; j < g.length; j++) {
            if (g[j] != r[j]) {
                throw new RuntimeException("verifyL " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    static void verifyF(String name, int i, float[] g, float[] r) {
        for (int j = 0; j < g.length; j++) {
            int gv = UNSAFE.getInt(g, UNSAFE.ARRAY_FLOAT_BASE_OFFSET + 4 * j);
            int rv = UNSAFE.getInt(r, UNSAFE.ARRAY_FLOAT_BASE_OFFSET + 4 * j);
            if (gv != rv) {
                throw new RuntimeException("verifyF " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + gv +
                                           " result[" + i + "][" + j + "] = " + rv);
            }
        }
    }

    static void verifyD(String name, int i, double[] g, double[] r) {
        for (int j = 0; j < g.length; j++) {
            long gv = UNSAFE.getLong(g, UNSAFE.ARRAY_DOUBLE_BASE_OFFSET + 8 * j);
            long rv = UNSAFE.getLong(r, UNSAFE.ARRAY_DOUBLE_BASE_OFFSET + 8 * j);
            if (gv != rv) {
                throw new RuntimeException("verifyF " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + gv +
                                           " result[" + i + "][" + j + "] = " + rv);
            }
        }
    }
}
