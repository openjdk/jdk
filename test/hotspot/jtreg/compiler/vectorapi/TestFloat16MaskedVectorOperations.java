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
* @bug 8386957
* @summary Verify that predicated (masked) Float16Vector operations generate
*          native predicated IR and do not fall back to a VectorBlend.
* @modules jdk.incubator.vector
* @library /test/lib /
* @compile TestFloat16MaskedVectorOperations.java
* @run driver compiler.vectorapi.TestFloat16MaskedVectorOperations
*/

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.*;
import static jdk.incubator.vector.Float16.*;
import static java.lang.Float.*;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

public class TestFloat16MaskedVectorOperations {
    static final VectorSpecies<Float16> SPECIES = Float16Vector.SPECIES_PREFERRED;
    static final int LEN = 1024;

    short[] input1;
    short[] input2;
    short[] input3;
    short[] output;
    boolean[] mask;

    public TestFloat16MaskedVectorOperations() {
        input1 = new short[LEN];
        input2 = new short[LEN];
        input3 = new short[LEN];
        output = new short[LEN];
        mask   = new boolean[LEN];

        Generator<Short> gen = G.float16s();
        for (int i = 0; i < LEN; ++i) {
            input1[i] = gen.next();
            input2[i] = gen.next();
            input3[i] = gen.next();
            mask[i]   = (i % 3) != 0;
        }
    }

    public static void main(String args[]) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    static void assertLane(short expected, short actual) {
        Verify.checkEQ(shortBitsToFloat16(actual), shortBitsToFloat16(expected));
    }

    @Test
    @IR(counts = {IRNode.ADD_VHF, " >0 "},
        failOn = {IRNode.VECTOR_BLEND_S},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {"vector_binOps_HF_mem_masked", " >0 "},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void maskedAddFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, mask, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                         .lanewise(VectorOperators.ADD,
                                   Float16Vector.fromArray(SPECIES, input2, i), m)
                         .intoArray(output, i);
        }
    }

    @Check(test="maskedAddFloat16")
    public void checkMaskedAdd() {
        for (int i = 0; i < LEN; ++i) {
            short expected = mask[i]
                ? floatToFloat16(float16ToFloat(input1[i]) + float16ToFloat(input2[i]))
                : input1[i];
            assertLane(expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.SUB_VHF, " >0 "},
        failOn = {IRNode.VECTOR_BLEND_S},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {"vector_binOps_HF_mem_masked", " >0 "},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void maskedSubFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, mask, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                         .lanewise(VectorOperators.SUB,
                                   Float16Vector.fromArray(SPECIES, input2, i), m)
                         .intoArray(output, i);
        }
    }

    @Check(test="maskedSubFloat16")
    public void checkMaskedSub() {
        for (int i = 0; i < LEN; ++i) {
            short expected = mask[i]
                ? floatToFloat16(float16ToFloat(input1[i]) - float16ToFloat(input2[i]))
                : input1[i];
            assertLane(expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.MUL_VHF, " >0 "},
        failOn = {IRNode.VECTOR_BLEND_S},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {"vector_binOps_HF_mem_masked", " >0 "},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void maskedMulFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, mask, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                         .lanewise(VectorOperators.MUL,
                                   Float16Vector.fromArray(SPECIES, input2, i), m)
                         .intoArray(output, i);
        }
    }

    @Check(test="maskedMulFloat16")
    public void checkMaskedMul() {
        for (int i = 0; i < LEN; ++i) {
            short expected = mask[i]
                ? floatToFloat16(float16ToFloat(input1[i]) * float16ToFloat(input2[i]))
                : input1[i];
            assertLane(expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.DIV_VHF, " >0 "},
        failOn = {IRNode.VECTOR_BLEND_S},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {"vector_binOps_HF_mem_masked", " >0 "},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void maskedDivFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, mask, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                         .lanewise(VectorOperators.DIV,
                                   Float16Vector.fromArray(SPECIES, input2, i), m)
                         .intoArray(output, i);
        }
    }

    @Check(test="maskedDivFloat16")
    public void checkMaskedDiv() {
        for (int i = 0; i < LEN; ++i) {
            short expected = mask[i]
                ? floatToFloat16(float16ToFloat(input1[i]) / float16ToFloat(input2[i]))
                : input1[i];
            assertLane(expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.SQRT_VHF, " >0 "},
        failOn = {IRNode.VECTOR_BLEND_S},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void maskedSqrtFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, mask, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                         .lanewise(VectorOperators.SQRT, m)
                         .intoArray(output, i);
        }
    }

    @Check(test="maskedSqrtFloat16")
    public void checkMaskedSqrt() {
        for (int i = 0; i < LEN; ++i) {
            short expected = mask[i]
                ? float16ToRawShortBits(sqrt(shortBitsToFloat16(input1[i])))
                : input1[i];
            assertLane(expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        failOn = {IRNode.VECTOR_BLEND_S},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void maskedFmaFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, mask, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                         .lanewise(VectorOperators.FMA,
                                   Float16Vector.fromArray(SPECIES, input2, i),
                                   Float16Vector.fromArray(SPECIES, input3, i), m)
                         .intoArray(output, i);
        }
    }

    @Check(test="maskedFmaFloat16")
    public void checkMaskedFma() {
        for (int i = 0; i < LEN; ++i) {
            short expected = mask[i]
                ? float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i]),
                                            shortBitsToFloat16(input3[i])))
                : input1[i];
            assertLane(expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(failOn = {IRNode.VECTOR_BLEND_S},
        applyIfCPUFeature = {"avx10_2", "true"})
    @IR(counts = {"vector_minmax_HF_mem_masked_avx10_2", " >0 "},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void maskedMinFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, mask, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                         .lanewise(VectorOperators.MIN,
                                   Float16Vector.fromArray(SPECIES, input2, i), m)
                         .intoArray(output, i);
        }
    }

    @Check(test="maskedMinFloat16")
    public void checkMaskedMin() {
        for (int i = 0; i < LEN; ++i) {
            short expected = mask[i]
                ? floatToFloat16(Math.min(float16ToFloat(input1[i]), float16ToFloat(input2[i])))
                : input1[i];
            assertLane(expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.MAX_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(failOn = {IRNode.VECTOR_BLEND_S},
        applyIfCPUFeature = {"avx10_2", "true"})
    @IR(counts = {"vector_minmax_HF_mem_masked_avx10_2", " >0 "},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature = {"avx10_2", "true"})
    public void maskedMaxFloat16() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, mask, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                         .lanewise(VectorOperators.MAX,
                                   Float16Vector.fromArray(SPECIES, input2, i), m)
                         .intoArray(output, i);
        }
    }

    @Check(test="maskedMaxFloat16")
    public void checkMaskedMax() {
        for (int i = 0; i < LEN; ++i) {
            short expected = mask[i]
                ? floatToFloat16(Math.max(float16ToFloat(input1[i]), float16ToFloat(input2[i])))
                : input1[i];
            assertLane(expected, output[i]);
        }
    }
}
