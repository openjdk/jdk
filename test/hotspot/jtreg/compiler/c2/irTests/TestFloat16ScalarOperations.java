/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Arm Limited. All rights reserved.
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

/**
* @test
* @bug 8308363 8336406
* @summary Validate compiler IR for various Float16 scalar operations.
* @modules jdk.incubator.vector
* @requires vm.compiler2.enabled
* @library /test/lib /
* @run driver TestFloat16ScalarOperations
*/
import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.*;
import java.util.Random;

import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

public class TestFloat16ScalarOperations {
    private static final int count = 1024;

    private short[] src;
    private short[] dst;
    private short res;

    private float[] fl;

    private static final Float16 ONE = valueOf(1.0f);
    private static final Float16 MONE = valueOf(-1.0f);
    private static final Float16 POSITIVE_ZERO = valueOf(0.0f);
    private static final Float16 NEGATIVE_ZERO = valueOf(-0.0f);
    private static final Float16 MIN_NORMAL = valueOf(0x1.0P-14f);
    private static final Float16 NEGATIVE_MAX_VALUE = valueOf(-0x1.ffcP+15f);
    private static final Float16 LT_MAX_HALF_ULP = Float16.valueOf(14.0f);
    private static final Float16 MAX_HALF_ULP = Float16.valueOf(16.0f);
    private static final Float16 SIGNALING_NAN = shortBitsToFloat16((short)31807);

    private static Generator<Float> genF = G.uniformFloats();
    private static Generator<Short> genHF = G.uniformFloat16s();

    private static final Float16 RANDOM1 = Float16.valueOf(genF.next());
    private static final Float16 RANDOM2 = Float16.valueOf(genF.next());
    private static final Float16 RANDOM3 = Float16.valueOf(genF.next());
    private static final Float16 RANDOM4 = Float16.valueOf(genF.next());
    private static final Float16 RANDOM5 = Float16.valueOf(genF.next());

    private static Float16 RANDOM1_VAR = RANDOM1;
    private static Float16 RANDOM2_VAR = RANDOM2;
    private static Float16 RANDOM3_VAR = RANDOM3;
    private static Float16 RANDOM4_VAR = RANDOM4;
    private static Float16 RANDOM5_VAR = RANDOM5;
    private static Float16 POSITIVE_ZERO_VAR = POSITIVE_ZERO;

    private static final float INEXACT_FP16 = 2051.0f;
    private static final float EXACT_FP16 = 2052.0f;
    private static final float SNAN_FP16 = Float.intBitsToFloat(0x7F8000F0);
    private static final float QNAN_FP16 = Float.intBitsToFloat(0x7FC00000);

    private Float16 GOLDEN_DIV_POT;
    private Float16 GOLDEN_MUL2;
    private short GOLDEN_INEXACT;
    private short GOLDEN_EXACT;
    private short GOLDEN_RANDOM_PAT1;
    private short GOLDEN_RANDOM_PAT2;
    private short GOLDEN_SNAN;
    private short GOLDEN_QNAN;

    public static void main(String args[]) {
        Scenario s0 = new Scenario(0, "--add-modules=jdk.incubator.vector", "-Xint");
        Scenario s1 = new Scenario(1, "--add-modules=jdk.incubator.vector");
        new TestFramework().addScenarios(s1).start();
    }

    public TestFloat16ScalarOperations() {
        src = new short[count];
        dst = new short[count];
        fl  = new float[count];

        G.fill(genF, fl);
        G.fill(genHF, src);

        GOLDEN_DIV_POT = testDivByPOT();
        GOLDEN_MUL2 = testMulByTWO();
        GOLDEN_INEXACT = testInexactFP16ConstantPatterns();
        GOLDEN_EXACT = testExactFP16ConstantPatterns();
        GOLDEN_RANDOM_PAT1 = testRandomFP16ConstantPatternSet1();
        GOLDEN_RANDOM_PAT2 = testRandomFP16ConstantPatternSet2();
        GOLDEN_SNAN = testSNaNFP16ConstantPatterns();
        GOLDEN_QNAN = testQNaNFP16ConstantPatterns();
    }

    static void assertResult(float actual, float expected, String msg) {
        if (actual != expected) {
            if (!Float.isNaN(actual) || !Float.isNaN(expected)) {
                String error = "TEST : " + msg + ": actual(" + actual + ") != expected(" + expected + ")";
                throw new AssertionError(error);
            }
        }
    }

    static void assertResult(float actual, float expected, String msg, int iter) {
        if (actual != expected) {
            if (!Float.isNaN(actual) || !Float.isNaN(expected)) {
                String error = "TEST (" + iter + "): " + msg + ": actual(" + actual + ") != expected(" + expected + ")";
                throw new AssertionError(error);
            }
        }
    }

    @Test
    @IR(counts = {"convF2HFAndS2HF", " >0 "}, phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {"convF2HFAndS2HF", " >0 "}, phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testconvF2HFAndS2HF() {
        for (int i = 0; i < count; i++) {
            // Transform the pattern (S2HF ConvF2HF) in this IR -
            // HF2S (AddHF (S2HF (ConvF2HF fl[i])), (S2HF (ConvF2HF fl[i])))
            // to a single convert operation after matching and eliminate redundant moves
            dst[i] = float16ToRawShortBits(add(valueOf(fl[i]), valueOf(fl[i])));
        }
    }

    @Test
    @IR(counts = {"convHF2SAndHF2F", " >0 "}, phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {"convHF2SAndHF2F", " >0 "}, phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testEliminateIntermediateHF2S() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            // Intermediate HF2S + S2HF is eliminated in following transformation
            // AddHF S2HF(HF2S (AddHF S2HF(src[i]), S2HF(0))), S2HF(src[i]) => AddHF (AddHF S2HF(src[i]), S2HF(0)), S2HF(src[i])
            // Also, the backend optimizes away the extra move while converting res to a float - ConvHF2F (S2HF (AddHF ..))
            res = add(add(res, shortBitsToFloat16(src[i])), shortBitsToFloat16(src[i]));
            dst[i] = (short)res.floatValue();
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testAdd1() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.add(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(failOn = {IRNode.ADD_HF, IRNode.REINTERPRET_S2HF, IRNode.REINTERPRET_HF2S},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(failOn = {IRNode.ADD_HF, IRNode.REINTERPRET_S2HF, IRNode.REINTERPRET_HF2S},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testAdd2() {
        Float16 hf0 = shortBitsToFloat16((short)0);
        Float16 hf1 = shortBitsToFloat16((short)15360);
        Float16 hf2 = shortBitsToFloat16((short)16384);
        Float16 hf3 = shortBitsToFloat16((short)16896);
        Float16 hf4 = shortBitsToFloat16((short)17408);
        res = float16ToRawShortBits(Float16.add(Float16.add(Float16.add(Float16.add(hf0, hf1), hf2), hf3), hf4));
    }

    @Test
    @IR(counts = {IRNode.SUB_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.SUB_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testSub() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.subtract(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.MUL_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.MUL_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testMul() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.multiply(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.DIV_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.DIV_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testDiv() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.divide(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.DIV_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.DIV_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testDivByOne() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.divide(shortBitsToFloat16(src[i]), ONE);
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.MAX_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.MAX_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testMax() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.max(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.MIN_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testMin() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.min(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.SQRT_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.SQRT_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testSqrt() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.sqrt(shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.FMA_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.FMA_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testFma() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            Float16 in = shortBitsToFloat16(src[i]);
            res = Float16.fma(in, in, in);
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.MUL_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.MUL_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public Float16 testDivByPOT() {
        Float16 res = valueOf(0.0f);
        for (int i = 0; i < 50; i++) {
            Float16 divisor = valueOf(8.0f);
            Float16 dividend = shortBitsToFloat16(src[i]);
            res = add(res, divide(dividend, divisor));
            divisor = valueOf(16.0f);
            res = add(res, divide(dividend, divisor));
            divisor = valueOf(32.0f);
            res = add(res, divide(dividend, divisor));
        }
        return res;
    }

    @Check(test="testDivByPOT")
    public void checkDivByPOT(Float16 actual) {
        Verify.checkEQ(Float16.float16ToRawShortBits(GOLDEN_DIV_POT), Float16.float16ToRawShortBits(actual));
    }

    @Test
    @IR(counts = {IRNode.MUL_HF, " 0 ", IRNode.ADD_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.MUL_HF, " 0 ", IRNode.ADD_HF, " >0 ", IRNode.REINTERPRET_S2HF, " >0 ", IRNode.REINTERPRET_HF2S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public Float16 testMulByTWO() {
        Float16 res = valueOf(0.0f);
        Float16 multiplier = valueOf(2.0f);
        for (int i = 0; i < 20; i++) {
            Float16 multiplicand = valueOf((float)i);
            res = add(res, multiply(multiplicand, multiplier));
        }
        return res;
    }

    @Check(test="testMulByTWO")
    public void checkMulByTWO(Float16 actual) {
        Verify.checkEQ(Float16.float16ToRawShortBits(GOLDEN_MUL2), Float16.float16ToRawShortBits(actual));
    }

    @Test
    @IR(counts = {IRNode.ADD_HF, " 0 ", IRNode.SUB_HF, " 0 ", IRNode.MUL_HF, " 0 ", IRNode.DIV_HF, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, " 0 ", IRNode.SUB_HF, " 0 ", IRNode.MUL_HF, " 0 ", IRNode.DIV_HF, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    // Test point checks various floating point operations with exactly one floating
    // point constant value passed as left or right argument, it then downcasts the
    // result of computation to a float16 value. This pattern is used to infer a
    // float16 IR during idealization. Floating point constant input is not-representable
    // in float16 value range and is an inexact float16 value thereby preventing
    // float16 IR inference.
    public short testInexactFP16ConstantPatterns() {
        short res = 0;
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() + INEXACT_FP16);
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() - INEXACT_FP16);
        res += Float.floatToFloat16(INEXACT_FP16 * POSITIVE_ZERO_VAR.floatValue());
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() / INEXACT_FP16);
        return res;
    }

    @Check(test="testInexactFP16ConstantPatterns")
    public void checkInexactFP16ConstantPatterns(short actual) {
        Verify.checkEQ(GOLDEN_INEXACT, actual);
    }

    @Test
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public short testSNaNFP16ConstantPatterns() {
        short res = 0;
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() + SNAN_FP16);
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() - SNAN_FP16);
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() * SNAN_FP16);
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() / SNAN_FP16);
        return res;
    }

    @Check(test="testSNaNFP16ConstantPatterns")
    public void checkSNaNFP16ConstantPatterns(short actual) {
        Verify.checkEQ(GOLDEN_SNAN, actual);
    }

    @Test
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public short testQNaNFP16ConstantPatterns() {
        short res = 0;
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() + QNAN_FP16);
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() - QNAN_FP16);
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() * QNAN_FP16);
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() / QNAN_FP16);
        return res;
    }

    @Check(test="testQNaNFP16ConstantPatterns")
    public void checkQNaNFP16ConstantPatterns(short actual) {
        Verify.checkEQ(GOLDEN_QNAN, actual);
    }

    @Test
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    // Test point checks various floating point operations with exactly one floating
    // point constant value passed as left or right argument, it then downcasts the
    // result of computation to a float16 value. This pattern is used to infer a
    // Float16 IR during idealization. Floating point constant input is representable
    // in Float16 value range thereby leading to a successful Float16 IR inference.
    public short testExactFP16ConstantPatterns() {
        short res = 0;
        res += Float.floatToFloat16(EXACT_FP16 + POSITIVE_ZERO_VAR.floatValue());
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() - EXACT_FP16);
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() * EXACT_FP16);
        res += Float.floatToFloat16(POSITIVE_ZERO_VAR.floatValue() / EXACT_FP16);
        return res;
    }

    @Check(test="testExactFP16ConstantPatterns")
    public void checkExactFP16ConstantPatterns(short actual) {
        Verify.checkEQ(GOLDEN_EXACT, actual);
    }

    @Test
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public short testRandomFP16ConstantPatternSet1() {
        short res = 0;
        res += Float.floatToFloat16(RANDOM1_VAR.floatValue() + RANDOM2.floatValue());
        res += Float.floatToFloat16(RANDOM2_VAR.floatValue() - RANDOM3.floatValue());
        res += Float.floatToFloat16(RANDOM3_VAR.floatValue() * RANDOM4.floatValue());
        res += Float.floatToFloat16(RANDOM4_VAR.floatValue() / RANDOM5.floatValue());
        return res;
    }

    @Check(test="testRandomFP16ConstantPatternSet1")
    public void checkRandomFP16ConstantPatternSet1(short actual) {
        Verify.checkEQ(GOLDEN_RANDOM_PAT1, actual);
    }


    @Test
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ", IRNode.DIV_HF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public short testRandomFP16ConstantPatternSet2() {
        short res = 0;
        res += Float.floatToFloat16(RANDOM2.floatValue() + RANDOM1_VAR.floatValue());
        res += Float.floatToFloat16(RANDOM3.floatValue() - RANDOM2_VAR.floatValue());
        res += Float.floatToFloat16(RANDOM4.floatValue() * RANDOM3_VAR.floatValue());
        res += Float.floatToFloat16(RANDOM5.floatValue() / RANDOM4_VAR.floatValue());
        return res;
    }

    @Check(test="testRandomFP16ConstantPatternSet2")
    public void checkRandomFP16ConstantPatternSet2(short actual) {
        Verify.checkEQ(GOLDEN_RANDOM_PAT2, actual);
    }

    //
    // Tests points for various Float16 constant folding transforms. Following figure represents various
    // special IEEE 754 binary16 values on a number line
    //
    //   -Inf                               -0.0                              Inf
    //   -------|-----------------------------|----------------------------|------
    //     -MAX_VALUE                        0.0                        MAX_VALUE
    //
    //  Number whose exponent lie between -14 and 15, both values inclusive, belongs to normal value range.
    //  IEEE 754 binary16 specification allows graceful degradation of numbers with exponents less than -14
    //  into a sub-normal value range i.e. their exponents may extend uptill -24, this is because format
    //  supports 10 mantissa bits which can be used to represent a number with exponents less than -14.
    //
    //  A number below the sub-normal value range is considered as 0.0. With regards to overflowing
    //  semantics, a value equal to or greater than MAX_VALUE + half ulp (MAX_VALUE) is considered as
    //  an Infinite value on both side of axis.
    //
    //  In addition, format specifies special bit representation for +Inf, -Inf and NaN values.
    //
    //  Tests also covers special cases for various operations as per Java SE specification.
    //


    @Test
    @IR(counts = {IRNode.ADD_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testAddConstantFolding() {
        // If either value is NaN, then the result is NaN.
        assertResult(add(Float16.NaN, valueOf(2.0f)).floatValue(), Float.NaN, "testAddConstantFolding");
        assertResult(add(Float16.NaN, Float16.NaN).floatValue(), Float.NaN, "testAddConstantFolding");
        assertResult(add(Float16.NaN, Float16.POSITIVE_INFINITY).floatValue(), Float.NaN, "testAddConstantFolding");

        // The sum of two infinities of opposite sign is NaN.
        assertResult(add(Float16.POSITIVE_INFINITY, Float16.NEGATIVE_INFINITY).floatValue(), Float.NaN, "testAddConstantFolding");

        // The sum of two infinities of the same sign is the infinity of that sign.
        assertResult(add(Float16.POSITIVE_INFINITY, Float16.POSITIVE_INFINITY).floatValue(), Float.POSITIVE_INFINITY, "testAddConstantFolding");
        assertResult(add(Float16.NEGATIVE_INFINITY, Float16.NEGATIVE_INFINITY).floatValue(), Float.NEGATIVE_INFINITY, "testAddConstantFolding");

        // The sum of an infinity and a finite value is equal to the infinite operand.
        assertResult(add(Float16.POSITIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.POSITIVE_INFINITY, "testAddConstantFolding");
        assertResult(add(Float16.NEGATIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.NEGATIVE_INFINITY, "testAddConstantFolding");

        // The sum of two zeros of opposite sign is positive zero.
        assertResult(add(NEGATIVE_ZERO, POSITIVE_ZERO).floatValue(), 0.0f, "testAddConstantFolding");

        // The sum of two zeros of the same sign is the zero of that sign.
        assertResult(add(NEGATIVE_ZERO, NEGATIVE_ZERO).floatValue(), -0.0f, "testAddConstantFolding");

        // The sum of a zero and a nonzero finite value is equal to the nonzero operand.
        assertResult(add(POSITIVE_ZERO, valueOf(2.0f)).floatValue(), 2.0f, "testAddConstantFolding");
        assertResult(add(NEGATIVE_ZERO, valueOf(2.0f)).floatValue(), 2.0f, "testAddConstantFolding");

        // Number equal to MAX_VALUE when added to half upl for MAX_VALUE results into Inf.
        assertResult(add(Float16.MAX_VALUE, MAX_HALF_ULP).floatValue(), Float.POSITIVE_INFINITY, "testAddConstantFolding");

        // If the magnitude of the sum is too large to represent, we say the operation
        // overflows; the result is then an infinity of appropriate sign.
        assertResult(add(Float16.MAX_VALUE, Float16.MAX_VALUE).floatValue(), Float.POSITIVE_INFINITY, "testAddConstantFolding");

        // Number equal to MAX_VALUE when added to half upl for MAX_VALUE results into MAX_VALUE.
        assertResult(add(Float16.MAX_VALUE, LT_MAX_HALF_ULP).floatValue(), Float16.MAX_VALUE.floatValue(), "testAddConstantFolding");

        assertResult(add(valueOf(1.0f), valueOf(2.0f)).floatValue(), 3.0f, "testAddConstantFolding");
    }

    @Test
    @IR(counts = {IRNode.SUB_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.SUB_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public void testSubConstantFolding() {
        // If either value is NaN, then the result is NaN.
        assertResult(subtract(Float16.NaN, valueOf(2.0f)).floatValue(), Float.NaN, "testSubConstantFolding");
        assertResult(subtract(Float16.NaN, Float16.NaN).floatValue(), Float.NaN, "testSubConstantFolding");
        assertResult(subtract(Float16.NaN, Float16.POSITIVE_INFINITY).floatValue(), Float.NaN, "testSubConstantFolding");

        // The difference of two infinities of opposite sign is NaN.
        assertResult(subtract(Float16.POSITIVE_INFINITY, Float16.NEGATIVE_INFINITY).floatValue(), Float.POSITIVE_INFINITY, "testSubConstantFolding");

        // The difference of two infinities of the same sign is NaN.
        assertResult(subtract(Float16.POSITIVE_INFINITY, Float16.POSITIVE_INFINITY).floatValue(), Float.NaN, "testSubConstantFolding");
        assertResult(subtract(Float16.NEGATIVE_INFINITY, Float16.NEGATIVE_INFINITY).floatValue(), Float.NaN, "testSubConstantFolding");

        // The difference of an infinity and a finite value is equal to the infinite operand.
        assertResult(subtract(Float16.POSITIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.POSITIVE_INFINITY, "testSubConstantFolding");
        assertResult(subtract(Float16.NEGATIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.NEGATIVE_INFINITY, "testSubConstantFolding");

        // The difference of two zeros of opposite sign is positive zero.
        assertResult(subtract(NEGATIVE_ZERO, POSITIVE_ZERO).floatValue(), 0.0f, "testSubConstantFolding");

        // Number equal to -MAX_VALUE when subtracted by half upl of MAX_VALUE results into -Inf.
        assertResult(subtract(NEGATIVE_MAX_VALUE, MAX_HALF_ULP).floatValue(), Float.NEGATIVE_INFINITY, "testSubConstantFolding");

        // Number equal to -MAX_VALUE when subtracted by a number less than half upl for MAX_VALUE results into -MAX_VALUE.
        assertResult(subtract(NEGATIVE_MAX_VALUE, LT_MAX_HALF_ULP).floatValue(), NEGATIVE_MAX_VALUE.floatValue(), "testSubConstantFolding");

        assertResult(subtract(valueOf(1.0f), valueOf(2.0f)).floatValue(), -1.0f, "testSubConstantFolding");
    }

    @Test
    @IR(counts = {IRNode.MAX_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.MAX_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public void testMaxConstantFolding() {
        // If either value is NaN, then the result is NaN.
        assertResult(max(valueOf(2.0f), Float16.NaN).floatValue(), Float.NaN, "testMaxConstantFolding");
        assertResult(max(Float16.NaN, Float16.NaN).floatValue(), Float.NaN, "testMaxConstantFolding");

        // This operation considers negative zero to be strictly smaller than positive zero
        assertResult(max(POSITIVE_ZERO, NEGATIVE_ZERO).floatValue(), 0.0f, "testMaxConstantFolding");

        // Other cases.
        assertResult(max(Float16.POSITIVE_INFINITY, Float16.NEGATIVE_INFINITY).floatValue(), Float.POSITIVE_INFINITY, "testMaxConstantFolding");
        assertResult(max(valueOf(1.0f), valueOf(2.0f)).floatValue(), 2.0f, "testMaxConstantFolding");
        assertResult(max(Float16.MAX_VALUE, Float16.MIN_VALUE).floatValue(), Float16.MAX_VALUE.floatValue(), "testMaxConstantFolding");
    }


    @Test
    @IR(counts = {IRNode.MIN_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.MIN_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public void testMinConstantFolding() {
        // If either value is NaN, then the result is NaN.
        assertResult(min(valueOf(2.0f), Float16.NaN).floatValue(), Float.NaN, "testMinConstantFolding");
        assertResult(min(Float16.NaN, Float16.NaN).floatValue(), Float.NaN, "testMinConstantFolding");

        // This operation considers negative zero to be strictly smaller than positive zero
        assertResult(min(POSITIVE_ZERO, NEGATIVE_ZERO).floatValue(), -0.0f, "testMinConstantFolding");

        // Other cases.
        assertResult(min(Float16.POSITIVE_INFINITY, Float16.NEGATIVE_INFINITY).floatValue(), Float.NEGATIVE_INFINITY, "testMinConstantFolding");
        assertResult(min(valueOf(1.0f), valueOf(2.0f)).floatValue(), 1.0f, "testMinConstantFolding");
        assertResult(min(Float16.MAX_VALUE, Float16.MIN_VALUE).floatValue(), Float16.MIN_VALUE.floatValue(), "testMinConstantFolding");
    }

    @Test
    @IR(counts = {IRNode.DIV_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.DIV_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public void testDivConstantFolding() {
        // If either value is NaN, then the result is NaN.
        assertResult(divide(Float16.NaN, POSITIVE_ZERO).floatValue(), Float.NaN, "testDivConstantFolding");
        assertResult(divide(NEGATIVE_ZERO, Float16.NaN).floatValue(), Float.NaN, "testDivConstantFolding");

        // Division of an infinity by an infinity results in NaN.
        assertResult(divide(Float16.NEGATIVE_INFINITY, Float16.POSITIVE_INFINITY).floatValue(), Float.NaN, "testDivConstantFolding");

        // Division of an infinity by a finite value results in a signed infinity. Sign of the result is positive if both operands have
        // the same sign, and negative if the operands have different signs
        assertResult(divide(Float16.NEGATIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.NEGATIVE_INFINITY, "testDivConstantFolding");
        assertResult(divide(Float16.POSITIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.POSITIVE_INFINITY, "testDivConstantFolding");

        // Division of a finite value by an infinity results in a signed zero. The sign is
        // determined by the above rule.
        assertResult(divide(valueOf(2.0f), Float16.POSITIVE_INFINITY).floatValue(), 0.0f, "testDivConstantFolding");
        assertResult(divide(valueOf(2.0f), Float16.NEGATIVE_INFINITY).floatValue(), -0.0f, "testDivConstantFolding");

        // Division of a zero by a zero results in NaN; division of zero by any other finite
        // value results in a signed zero. The sign is determined by the rule stated above.
        assertResult(divide(POSITIVE_ZERO, NEGATIVE_ZERO).floatValue(), Float.NaN, "testDivConstantFolding");
        assertResult(divide(POSITIVE_ZERO, Float16.MAX_VALUE).floatValue(), 0.0f, "testDivConstantFolding");
        assertResult(divide(NEGATIVE_ZERO, Float16.MAX_VALUE).floatValue(), -0.0f, "testDivConstantFolding");

        // Division of a nonzero finite value by a zero results in a signed infinity. The sign
        // is determined by the rule stated above
        assertResult(divide(valueOf(2.0f), NEGATIVE_ZERO).floatValue(), Float.NEGATIVE_INFINITY, "testDivConstantFolding");
        assertResult(divide(valueOf(2.0f), POSITIVE_ZERO).floatValue(), Float.POSITIVE_INFINITY, "testDivConstantFolding");

        // If the magnitude of the quotient is too large to represent, we say the operation
        // overflows; the result is then an infinity of appropriate sign.
        assertResult(divide(Float16.MAX_VALUE, Float16.MIN_NORMAL).floatValue(), Float.POSITIVE_INFINITY, "testDivConstantFolding");
        assertResult(divide(Float16.MAX_VALUE, valueOf(-0x1.0P-14f)).floatValue(), Float.NEGATIVE_INFINITY, "testDivConstantFolding");

        assertResult(divide(valueOf(2.0f), valueOf(2.0f)).floatValue(), 1.0f, "testDivConstantFolding");
    }

    @Test
    @IR(counts = {IRNode.MUL_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.MUL_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public void testMulConstantFolding() {
        // If any operand is NaN, the result is NaN.
        assertResult(multiply(Float16.NaN, valueOf(4.0f)).floatValue(), Float.NaN, "testMulConstantFolding");
        assertResult(multiply(Float16.NaN, Float16.NaN).floatValue(), Float.NaN, "testMulConstantFolding");

        // Multiplication of an infinity by a zero results in NaN.
        assertResult(multiply(Float16.POSITIVE_INFINITY, POSITIVE_ZERO).floatValue(), Float.NaN, "testMulConstantFolding");

        // Multiplication of an infinity by a finite value results in a signed infinity.
        assertResult(multiply(Float16.POSITIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.POSITIVE_INFINITY, "testMulConstantFolding");
        assertResult(multiply(Float16.NEGATIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.NEGATIVE_INFINITY, "testMulConstantFolding");

        // If the magnitude of the product is too large to represent, we say the operation
        // overflows; the result is then an infinity of appropriate sign
        assertResult(multiply(Float16.MAX_VALUE, Float16.MAX_VALUE).floatValue(), Float.POSITIVE_INFINITY, "testMulConstantFolding");
        assertResult(multiply(NEGATIVE_MAX_VALUE, Float16.MAX_VALUE).floatValue(), Float.NEGATIVE_INFINITY, "testMulConstantFolding");

        assertResult(multiply(multiply(multiply(valueOf(1.0f), valueOf(2.0f)), valueOf(3.0f)), valueOf(4.0f)).floatValue(), 1.0f * 2.0f * 3.0f * 4.0f, "testMulConstantFolding");
    }

    @Test
    @IR(counts = {IRNode.SQRT_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.SQRT_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public void testSqrtConstantFolding() {
        // If the argument is NaN or less than zero, then the result is NaN.
        assertResult(sqrt(Float16.NaN).floatValue(), Float.NaN, "testSqrtConstantFolding");
        assertResult(sqrt(SIGNALING_NAN).floatValue(), Float.NaN, "testSqrtConstantFolding");

        // If the argument is positive infinity, then the result is positive infinity.
        assertResult(sqrt(Float16.POSITIVE_INFINITY).floatValue(), Float.POSITIVE_INFINITY, "testSqrtConstantFolding");

        // If the argument is positive zero or negative zero, then the result is the same as the argument.
        assertResult(sqrt(POSITIVE_ZERO).floatValue(), 0.0f, "testSqrtConstantFolding");
        assertResult(sqrt(NEGATIVE_ZERO).floatValue(), -0.0f, "testSqrtConstantFolding");

        // Other cases.
        assertResult(Math.round(sqrt(valueOf(0x1.ffcP+14f)).floatValue()), Math.round(Math.sqrt(0x1.ffcP+14f)), "testSqrtConstantFolding");
    }

    @Test
    @IR(counts = {IRNode.FMA_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.FMA_HF, " 0 ", IRNode.REINTERPRET_S2HF, " 0 ", IRNode.REINTERPRET_HF2S, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public void testFMAConstantFolding() {
        // If any argument is NaN, the result is NaN.
        assertResult(fma(Float16.NaN, valueOf(2.0f), valueOf(3.0f)).floatValue(), Float.NaN, "testFMAConstantFolding");
        assertResult(fma(SIGNALING_NAN, valueOf(2.0f), valueOf(3.0f)).floatValue(), Float.NaN, "testFMAConstantFolding");
        assertResult(fma(valueOf(2.0f), Float16.NaN, valueOf(3.0f)).floatValue(), Float.NaN, "testFMAConstantFolding");

        assertResult(fma(shortBitsToFloat16(Float.floatToFloat16(2.0f)),
                         shortBitsToFloat16(Float.floatToFloat16(3.0f)),
                         Float16.NaN).floatValue(), Float.NaN, "testFMAConstantFolding");

        // If one of the first two arguments is infinite and the other is zero, the result is NaN.
        assertResult(fma(Float16.POSITIVE_INFINITY, POSITIVE_ZERO, valueOf(2.0f)).floatValue(), Float.NaN, "testFMAConstantFolding");
        assertResult(fma(Float16.POSITIVE_INFINITY, NEGATIVE_ZERO, valueOf(2.0f)).floatValue(), Float.NaN, "testFMAConstantFolding");
        assertResult(fma(NEGATIVE_ZERO, Float16.POSITIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.NaN, "testFMAConstantFolding");
        assertResult(fma(POSITIVE_ZERO, Float16.POSITIVE_INFINITY, valueOf(2.0f)).floatValue(), Float.NaN, "testFMAConstantFolding");

        // If the exact product of the first two arguments is infinite (in other words, at least one of the arguments is infinite
        // and the other is neither zero nor NaN) and the third argument is an infinity of the opposite sign, the result is NaN.
        assertResult(fma(valueOf(2.0f), Float16.POSITIVE_INFINITY, Float16.NEGATIVE_INFINITY).floatValue(), Float.NaN, "testFMAConstantFolding");
        assertResult(fma(valueOf(2.0f), Float16.NEGATIVE_INFINITY, Float16.POSITIVE_INFINITY).floatValue(), Float.NaN, "testFMAConstantFolding");
        assertResult(fma(Float16.POSITIVE_INFINITY, valueOf(2.0f), Float16.NEGATIVE_INFINITY).floatValue(), Float.NaN, "testFMAConstantFolding");
        assertResult(fma(Float16.NEGATIVE_INFINITY, valueOf(2.0f), Float16.POSITIVE_INFINITY).floatValue(), Float.NaN, "testFMAConstantFolding");

        // Signed bits.
        assertResult(fma(NEGATIVE_ZERO, POSITIVE_ZERO, POSITIVE_ZERO).floatValue(), 0.0f, "testFMAConstantFolding");
        assertResult(fma(NEGATIVE_ZERO, POSITIVE_ZERO, NEGATIVE_ZERO).floatValue(), -0.0f, "testFMAConstantFolding");

        assertResult(fma(Float16.POSITIVE_INFINITY, valueOf(2.0f), valueOf(3.0f)).floatValue(), Float.POSITIVE_INFINITY, "testFMAConstantFolding");
        assertResult(fma(Float16.NEGATIVE_INFINITY, valueOf(2.0f), valueOf(3.0f)).floatValue(), Float.NEGATIVE_INFINITY, "testFMAConstantFolding");
        assertResult(fma(valueOf(1.0f), valueOf(2.0f), valueOf(3.0f)).floatValue(), 1.0f * 2.0f + 3.0f, "testFMAConstantFolding");
    }

    @Test
    @IR(failOn = {IRNode.ADD_HF, IRNode.SUB_HF, IRNode.MUL_HF, IRNode.DIV_HF, IRNode.SQRT_HF, IRNode.FMA_HF},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(failOn = {IRNode.ADD_HF, IRNode.SUB_HF, IRNode.MUL_HF, IRNode.DIV_HF, IRNode.SQRT_HF, IRNode.FMA_HF},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public void testRounding1() {
        dst[0] = float16ToRawShortBits(add(RANDOM1, RANDOM2));
        dst[1] = float16ToRawShortBits(subtract(RANDOM2, RANDOM3));
        dst[2] = float16ToRawShortBits(multiply(RANDOM4, RANDOM5));
        dst[3] = float16ToRawShortBits(sqrt(RANDOM2));
        dst[4] = float16ToRawShortBits(fma(RANDOM3, RANDOM4, RANDOM5));
        dst[5] = float16ToRawShortBits(divide(RANDOM5, RANDOM4));
    }

    @Check(test = "testRounding1", when = CheckAt.COMPILED)
    public void checkRounding1() {
        assertResult(dst[0], Float.floatToFloat16(RANDOM1.floatValue() + RANDOM2.floatValue()),
                     "testRounding1 case1a");
        assertResult(dst[0], float16ToRawShortBits(add(RANDOM1, RANDOM2)), "testRounding1 case1b");

        assertResult(dst[1], Float.floatToFloat16(RANDOM2.floatValue() - RANDOM3.floatValue()),
                     "testRounding1 case2a");
        assertResult(dst[1], float16ToRawShortBits(subtract(RANDOM2, RANDOM3)), "testRounding1 case2b");

        assertResult(dst[2], Float.floatToFloat16(RANDOM4.floatValue() * RANDOM5.floatValue()),
                     "testRounding1 case3a");
        assertResult(dst[2], float16ToRawShortBits(multiply(RANDOM4, RANDOM5)), "testRounding1 cast3b");

        assertResult(dst[3], Float.floatToFloat16((float)Math.sqrt(RANDOM2.floatValue())), "testRounding1 case4a");
        assertResult(dst[3], float16ToRawShortBits(sqrt(RANDOM2)), "testRounding1 case4a");

        assertResult(dst[4], Float.floatToFloat16(Math.fma(RANDOM3.floatValue(), RANDOM4.floatValue(),
                     RANDOM5.floatValue())), "testRounding1 case5a");
        assertResult(dst[4], float16ToRawShortBits(fma(RANDOM3, RANDOM4, RANDOM5)), "testRounding1 case5b");

        assertResult(dst[5], Float.floatToFloat16(RANDOM5.floatValue() / RANDOM4.floatValue()),
                     "testRounding1 case6a");
        assertResult(dst[5], float16ToRawShortBits(divide(RANDOM5, RANDOM4)), "testRounding1 case6b");
    }

    @Test
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ",
                  IRNode.DIV_HF, " >0 ", IRNode.SQRT_HF, " >0 ", IRNode.FMA_HF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, " >0 ", IRNode.SUB_HF, " >0 ", IRNode.MUL_HF, " >0 ",
                  IRNode.DIV_HF, " >0 ", IRNode.SQRT_HF, " >0 ", IRNode.FMA_HF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    @Warmup(10000)
    public void testRounding2() {
        dst[0] = float16ToRawShortBits(add(RANDOM1_VAR, RANDOM2_VAR));
        dst[1] = float16ToRawShortBits(subtract(RANDOM2_VAR, RANDOM3_VAR));
        dst[2] = float16ToRawShortBits(multiply(RANDOM4_VAR, RANDOM5_VAR));
        dst[3] = float16ToRawShortBits(sqrt(RANDOM2_VAR));
        dst[4] = float16ToRawShortBits(fma(RANDOM3_VAR, RANDOM4_VAR, RANDOM5_VAR));
        dst[5] = float16ToRawShortBits(divide(RANDOM5_VAR, RANDOM4_VAR));
    }

    @Check(test = "testRounding2", when = CheckAt.COMPILED)
    public void checkRounding2() {
        assertResult(dst[0], Float.floatToFloat16(RANDOM1_VAR.floatValue() + RANDOM2_VAR.floatValue()),
                     "testRounding2 case1a");
        assertResult(dst[0], float16ToRawShortBits(add(RANDOM1_VAR, RANDOM2_VAR)), "testRounding2 case1b");

        assertResult(dst[1], Float.floatToFloat16(RANDOM2_VAR.floatValue() - RANDOM3_VAR.floatValue()),
                     "testRounding2 case2a");
        assertResult(dst[1], float16ToRawShortBits(subtract(RANDOM2_VAR, RANDOM3_VAR)), "testRounding2 case2b");

        assertResult(dst[2], Float.floatToFloat16(RANDOM4_VAR.floatValue() * RANDOM5_VAR.floatValue()),
                     "testRounding2 case3a");
        assertResult(dst[2], float16ToRawShortBits(multiply(RANDOM4_VAR, RANDOM5_VAR)), "testRounding2 cast3b");

        assertResult(dst[3], Float.floatToFloat16((float)Math.sqrt(RANDOM2_VAR.floatValue())), "testRounding2 case4a");
        assertResult(dst[3], float16ToRawShortBits(sqrt(RANDOM2_VAR)), "testRounding2 case4a");

        assertResult(dst[4], Float.floatToFloat16(Math.fma(RANDOM3_VAR.floatValue(), RANDOM4_VAR.floatValue(),
                     RANDOM5_VAR.floatValue())), "testRounding2 case5a");
        assertResult(dst[4], float16ToRawShortBits(fma(RANDOM3_VAR, RANDOM4_VAR, RANDOM5_VAR)), "testRounding2 case5b");

        assertResult(dst[5], Float.floatToFloat16(RANDOM5_VAR.floatValue() / RANDOM4_VAR.floatValue()),
                     "testRounding2 case6a");
        assertResult(dst[5], float16ToRawShortBits(divide(RANDOM5_VAR, RANDOM4_VAR)), "testRounding2 case6b");
    }
}
