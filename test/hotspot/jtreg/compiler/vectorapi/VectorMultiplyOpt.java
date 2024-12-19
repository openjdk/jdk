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

package compiler.vectorapi;

import jdk.incubator.vector.*;
import java.util.Random;
import java.util.stream.IntStream;
import compiler.lib.ir_framework.*;
import java.lang.reflect.Array;

/**
 * @test
 * @bug 8341137
 * @summary Optimize long vector multiplication using x86 VPMUL[U]DQ instruction.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.VectorMultiplyOpt
 */

public class VectorMultiplyOpt {

    public static int[] isrc1;
    public static int[] isrc2;
    public static long[] lsrc1;
    public static long[] lsrc2;
    public static long[] res;

    public static final int SIZE = 1024;
    public static final Random r = jdk.test.lib.Utils.getRandomInstance();
    public static final VectorSpecies<Long> LSP = LongVector.SPECIES_PREFERRED;
    public static final VectorSpecies<Integer> ISP = IntVector.SPECIES_PREFERRED;

    public static final long mask1 = r.nextLong(0xFFFFFFFFL);
    public static final long mask2 = r.nextLong(0xFFFFFFFFL);
    public static final long mask3 = r.nextLong(0xFFFFFFFFL);
    public static final long mask4 = r.nextLong(0xFFFFFFFFL);
    public static final long mask5 = r.nextLong(0xFFFFFFFFL);
    public static final long mask6 = r.nextLong(0xFFFFFFFFL);

    public static final int shift1 = r.nextInt(32) + 32;
    public static final int shift2 = r.nextInt(32) + 32;
    public static final int shift3 = r.nextInt(32) + 32;
    public static final int shift4 = r.nextInt(32) + 32;
    public static final int shift5 = r.nextInt(32) + 32;

    public VectorMultiplyOpt() {
        lsrc1 = new long[SIZE];
        lsrc2 = new long[SIZE];
        res  = new long[SIZE];
        isrc1 = new int[SIZE + 16];
        isrc2 = new int[SIZE + 16];
        IntStream.range(0, SIZE).forEach(i -> { lsrc1[i] = Long.MAX_VALUE * r.nextLong(); });
        IntStream.range(0, SIZE).forEach(i -> { lsrc2[i] = Long.MAX_VALUE * r.nextLong(); });
        IntStream.range(0, SIZE).forEach(i -> { isrc1[i] = Integer.MAX_VALUE * r.nextInt(); });
        IntStream.range(0, SIZE).forEach(i -> { isrc2[i] = Integer.MAX_VALUE * r.nextInt(); });
    }


    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
        System.out.println("PASSED");
    }

    interface Validator {
        public long apply(long src1, long src2);
    }

    public static void validate(String msg, long[] actual, Object src1, Object src2, Validator func) {
        for (int i = 0; i < actual.length; i++) {
            long expected;
            if (long[].class == src1.getClass()) {
                expected = func.apply(Array.getLong(src1, i), Array.getLong(src2, i));
            } else {
                assert int[].class == src1.getClass();
                expected = func.apply(Array.getInt(src1, i), Array.getInt(src2, i));
            }
            if (actual[i] != expected) {
                throw new AssertionError(msg + "index " + i + ": src1 = " + Array.get(src1, i) + " src2 = " +
                                         Array.get(src2, i) + " actual = " + actual[i] + " expected = " + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.MUL_VL, " >0 ", IRNode.AND_VL, " >0 "}, applyIfCPUFeature = {"avx", "true"})
    @IR(counts = {"vmuludq", " >0 "}, phase = CompilePhase.FINAL_CODE, applyIfCPUFeature = {"avx", "true"})
    @Warmup(value = 10000)
    public static void test_pattern1() {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.AND, mask1)
                 .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.AND, mask1))
                 .intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = (lsrc1[i] & mask1) * (lsrc2[i] & mask1);
        }
    }

    @Check(test = "test_pattern1")
    public void test_pattern1_validate() {
        validate("pattern1 ", res, lsrc1, lsrc2, (l1, l2) -> (l1 & mask1) * (l2 & mask1));
    }

    @Test
    @IR(counts = {IRNode.MUL_VL, " >0 ", IRNode.AND_VL, " >0 ", IRNode.URSHIFT_VL, " >0 "}, applyIfCPUFeature = {"avx", "true"})
    @IR(counts = {"vmuludq", " >0 "}, phase = CompilePhase.FINAL_CODE, applyIfCPUFeature = {"avx", "true"})
    @Warmup(value = 10000)
    public static void test_pattern2() {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.AND, mask2)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.LSHR, shift1))
                .intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = (lsrc1[i] & mask2) * (lsrc2[i] >>> shift1);
        }
    }

    @Check(test = "test_pattern2")
    public void test_pattern2_validate() {
        validate("pattern2 ", res, lsrc1, lsrc2, (l1, l2) -> (l1 & mask2) * (l2 >>> shift1));
    }

    @Test
    @IR(counts = {IRNode.MUL_VL, " >0 ", IRNode.URSHIFT_VL, " >0 "}, applyIfCPUFeature = {"avx", "true"})
    @IR(counts = {"vmuludq", " >0 "}, phase = CompilePhase.FINAL_CODE, applyIfCPUFeature = {"avx", "true"})
    @Warmup(value = 10000)
    public static void test_pattern3() {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.LSHR, shift2)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.LSHR, shift3))
                .intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = (lsrc1[i] >>> shift2) * (lsrc2[i] >>> shift3);
        }
    }

    @Check(test = "test_pattern3")
    public void test_pattern3_validate() {
        validate("pattern3 ", res, lsrc1, lsrc2, (l1, l2) -> (l1 >>> shift2) * (l2 >>> shift3));
    }

    @Test
    @IR(counts = {IRNode.MUL_VL, " >0 ", IRNode.URSHIFT_VL, " >0 "}, applyIfCPUFeature = {"avx", "true"})
    @IR(counts = {"vmuludq", " >0 "}, applyIfCPUFeature = {"avx", "true"}, phase = CompilePhase.FINAL_CODE)
    @Warmup(value = 10000)
    public static void test_pattern4() {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.LSHR, shift4)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.AND, mask4))
                .intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = (lsrc1[i] >>> shift4) * (lsrc2[i] & mask4);
        }
    }

    @Check(test = "test_pattern4")
    public void test_pattern4_validate() {
        validate("pattern4 ", res, lsrc1, lsrc2, (l1, l2) -> (l1 >>> shift4) * (l2 & mask4));
    }

    @Test
    @IR(counts = {IRNode.MUL_VL, " >0 ", IRNode.VECTOR_CAST_I2L, " >0 "}, applyIfCPUFeature = {"avx", "true"})
    @IR(counts = {"vmuldq", " >0 "}, applyIfCPUFeature = {"avx", "true"}, phase = CompilePhase.FINAL_CODE)
    @Warmup(value = 10000)
    public static void test_pattern5() {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = IntVector.fromArray(ISP, isrc1, i)
                                        .convert(VectorOperators.I2L, 0)
                                        .reinterpretAsLongs();
            LongVector vsrc2 = IntVector.fromArray(ISP, isrc2, i)
                                        .convert(VectorOperators.I2L, 0)
                                        .reinterpretAsLongs();
            vsrc1.lanewise(VectorOperators.MUL, vsrc2).intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = Math.multiplyFull(isrc1[i], isrc2[i]);
        }
    }

    @Check(test = "test_pattern5")
    public void test_pattern5_validate() {
        validate("pattern5 ", res, isrc1, isrc2, (i1, i2) -> Math.multiplyFull((int)i1, (int)i2));
    }


    @Test
    @IR(counts = {IRNode.MUL_VL, " >0 ", IRNode.RSHIFT_VL, " >0 "}, applyIfCPUFeature = {"avx", "true"})
    @IR(counts = {"vmuldq", " >0 "}, applyIfCPUFeature = {"avx", "true"}, phase = CompilePhase.FINAL_CODE)
    @Warmup(value = 10000)
    public static void test_pattern6() {
        int i = 0;
        for (; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.ASHR, shift5)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.ASHR, shift5))
                .intoArray(res, i);
        }
        for (; i < res.length; i++) {
            res[i] = (lsrc1[i] >> shift5) * (lsrc2[i] >> shift5);
        }
    }

    @Check(test = "test_pattern6")
    public void test_pattern6_validate() {
        validate("pattern6 ", res, lsrc1, lsrc2, (l1, l2) -> (l1 >> shift5) * (l2 >> shift5));
    }

}
