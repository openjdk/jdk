/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8392212
 * @summary Element type of a Float16 constant should be TypeH.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.Float16;
import jdk.incubator.vector.Float16Vector;
import jdk.incubator.vector.VectorSpecies;
import static java.lang.Float.float16ToFloat;
import static java.lang.Float.floatToFloat16;
import static jdk.incubator.vector.Float16.shortBitsToFloat16;

public class TestFloat16ConstantInferencing {
    static final VectorSpecies<Float16> SPECIES = Float16Vector.SPECIES_PREFERRED;
    static final int LEN = 512;

    static final short FP16_ZERO = (short)0x0000;
    static final short FP16_ONE = (short)0x3C00;
    static final short FP16_TEN = floatToFloat16(10.0f);

    static short FP16_VAR = (short)0x4200;

    short[] input = new short[LEN];
    short[] output = new short[LEN];

    public TestFloat16ConstantInferencing() {
        for (int i = 0; i < LEN; i++) {
            input[i] = (short)(i * 17);
        }
    }

    public static void main(String args[]) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    // A constant zero lane folds to TypeH::ZERO, i.e. a ConH feeding the Replicate.
    @Test
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void broadcastZeroFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.broadcast(SPECIES, FP16_ZERO).intoArray(output, i);
        }
    }

    @Check(test = "broadcastZeroFloat16")
    void checkBroadcastZeroFloat16() {
        Float16 expectedHF = shortBitsToFloat16(FP16_ZERO);
        for (int i = 0; i < LEN; i++) {
            Verify.checkEQ(shortBitsToFloat16(output[i]), expectedHF);
        }
    }

    // A constant one lane folds to TypeH::ONE, i.e. a ConH feeding the Replicate.
    @Test
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void broadcastOneFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.broadcast(SPECIES, FP16_ONE).intoArray(output, i);
        }
    }

    @Check(test = "broadcastOneFloat16")
    void checkBroadcastOneFloat16() {
        Float16 expectedHF = shortBitsToFloat16(FP16_ONE);
        for (int i = 0; i < LEN; i++) {
            Verify.checkEQ(shortBitsToFloat16(output[i]), expectedHF);
        }
    }

    @Test
    @IR(failOn = {IRNode.REINTERPRET_S2HF, IRNode.CON_H},
        counts = {IRNode.REPLICATE_S, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(failOn = {IRNode.REINTERPRET_S2HF, IRNode.CON_H},
        counts = {IRNode.REPLICATE_S, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void broadcastVariableFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.broadcast(SPECIES, FP16_VAR).intoArray(output, i);
        }
    }

    @Check(test = "broadcastVariableFloat16")
    void checkBroadcastVariableFloat16() {
        Float16 expectedHF = shortBitsToFloat16(FP16_VAR);
        for (int i = 0; i < LEN; i++) {
            Verify.checkEQ(shortBitsToFloat16(output[i]), expectedHF);
        }
    }

    // Auto-vectorization infers AddHF. The constant operand becomes ConH and is replicated,
    // the same shape as a Vector API broadcast of that constant.
    @Test
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 ", IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"},
        applyIf = {"UseSuperWord", "true"})
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 ", IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"},
        applyIf = {"UseSuperWord", "true"})
    void autoVecAddConstant() {
        for (int i = 0; i < LEN; i++) {
            output[i] = floatToFloat16(float16ToFloat(FP16_TEN) + float16ToFloat(input[i]));
        }
    }

    @Check(test = "autoVecAddConstant")
    void checkAutoVecAddConstant() {
        for (int i = 0; i < LEN; i++) {
            Float16 expectedHF = shortBitsToFloat16(floatToFloat16(float16ToFloat(FP16_TEN) + float16ToFloat(input[i])));
            Verify.checkEQ(shortBitsToFloat16(output[i]), expectedHF);
        }
    }

    @Test
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 ", IRNode.SUB_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"},
        applyIf = {"UseSuperWord", "true"})
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 ", IRNode.SUB_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"},
        applyIf = {"UseSuperWord", "true"})
    void autoVecSubConstant() {
        for (int i = 0; i < LEN; i++) {
            output[i] = floatToFloat16(float16ToFloat(input[i]) - float16ToFloat(FP16_TEN));
        }
    }

    @Check(test = "autoVecSubConstant")
    void checkAutoVecSubConstant() {
        for (int i = 0; i < LEN; i++) {
            Float16 expectedHF = shortBitsToFloat16(floatToFloat16(float16ToFloat(input[i]) - float16ToFloat(FP16_TEN)));
            Verify.checkEQ(shortBitsToFloat16(output[i]), expectedHF);
        }
    }

    @Test
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 ", IRNode.MUL_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"},
        applyIf = {"UseSuperWord", "true"})
    @IR(counts = {IRNode.CON_H, " >0 ", IRNode.REPLICATE_S, " >0 ", IRNode.MUL_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"},
        applyIf = {"UseSuperWord", "true"})
    void autoVecMulConstant() {
        for (int i = 0; i < LEN; i++) {
            output[i] = floatToFloat16(float16ToFloat(input[i]) * float16ToFloat(FP16_TEN));
        }
    }

    @Check(test = "autoVecMulConstant")
    void checkAutoVecMulConstant() {
        for (int i = 0; i < LEN; i++) {
            Float16 expectedHF = shortBitsToFloat16(floatToFloat16(float16ToFloat(input[i]) * float16ToFloat(FP16_TEN)));
            Verify.checkEQ(shortBitsToFloat16(output[i]), expectedHF);
        }
    }
}
