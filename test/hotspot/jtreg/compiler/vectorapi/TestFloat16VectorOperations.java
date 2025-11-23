/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
* @bug 8370691
* @summary Test intrinsification of Float16Vector operations
* @modules jdk.incubator.vector
* @library /test/lib /
* @compile TestFloat16VectorOperations.java
* @run driver/timeout=480 compiler.vectorapi.TestFloat16VectorOperations
*/

package compiler.vectorapi;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import static jdk.incubator.vector.Float16.*;
import static java.lang.Float.*;
import java.util.Arrays;
import jdk.test.lib.*;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

public class TestFloat16VectorOperations {
    short[] input1;
    short[] input2;
    short[] input3;
    short[] output;
    static final int LEN = 2048;
    static short FP16_SCALAR = (short)0x7777;

    static final Float16 FP16_CONST = Float16.valueOf(1023.0f);
    static final VectorSpecies<Float16> SPECIES = Float16Vector.SPECIES_PREFERRED;

    public static void main(String args[]) {
        // Test with default MaxVectorSize
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");

        // Test with different values of MaxVectorSize
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:MaxVectorSize=8");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:MaxVectorSize=16");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:MaxVectorSize=32");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:MaxVectorSize=64");
    }

    static void assertResults(int arity, short ... values) {
        assert values.length == (arity + 2);
        Float16 expected_fp16 = shortBitsToFloat16(values[arity]);
        Float16 actual_fp16 = shortBitsToFloat16(values[arity + 1]);
        if(!expected_fp16.equals(actual_fp16)) {
            String inputs = Arrays.toString(Arrays.copyOfRange(values, 0, arity - 1));
            throw new AssertionError("Result Mismatch!, input = " + inputs + " actual = " + actual_fp16 +  " expected = " + expected_fp16);
        }
    }

    public TestFloat16VectorOperations() {
        input1 = new short[LEN];
        input2 = new short[LEN];
        input3 = new short[LEN];
        output = new short[LEN];

        short min_value = float16ToRawShortBits(Float16.MIN_VALUE);
        short max_value = float16ToRawShortBits(Float16.MAX_VALUE);
        Generator<Short> gen = G.float16s();
        for (int i = 0; i < LEN; ++i) {
            input1[i] = gen.next();
            input2[i] = gen.next();
            input3[i] = gen.next();
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorAddFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.ADD,
                                     Float16Vector.fromArray(SPECIES, input2, i))
                           .intoArray(output, i);
        }
        for (; i < LEN; i++) {
            output[i] = float16ToRawShortBits(
                            add(shortBitsToFloat16(input1[i]),
                                shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorAddFloat16")
    void checkResultAdd() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) + float16ToFloat(input2[i]));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }


    @Test
    @IR(counts = {IRNode.SUB_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.SUB_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorSubFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.SUB,
                                     Float16Vector.fromArray(SPECIES, input2, i))
                           .intoArray(output, i);
        }
        for (; i < LEN; i++) {
            output[i] = float16ToRawShortBits(
                            subtract(shortBitsToFloat16(input1[i]),
                                     shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorSubFloat16")
    void checkResultSub() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) - float16ToFloat(input2[i]));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }


    @Test
    @IR(counts = {IRNode.MUL_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.MUL_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorMulFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.MUL,
                                     Float16Vector.fromArray(SPECIES, input2, i))
                           .intoArray(output, i);
        }
        for (; i < LEN; i++) {
            output[i] = float16ToRawShortBits(
                            multiply(shortBitsToFloat16(input1[i]),
                                     shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMulFloat16")
    void checkResultMul() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) * float16ToFloat(input2[i]));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.DIV_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.DIV_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorDivFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.DIV,
                                     Float16Vector.fromArray(SPECIES, input2, i))
                           .intoArray(output, i);
        }
        for (; i < LEN; i++) {
            output[i] = float16ToRawShortBits(
                            divide(shortBitsToFloat16(input1[i]),
                                   shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorDivFloat16")
    void checkResultDiv() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) / float16ToFloat(input2[i]));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.MIN_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorMinFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.MIN,
                                     Float16Vector.fromArray(SPECIES, input2, i))
                           .intoArray(output, i);
        }
        for (; i < LEN; i++) {
            output[i] = float16ToRawShortBits(
                            min(shortBitsToFloat16(input1[i]),
                                shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMinFloat16")
    void checkResultMin() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.min(float16ToFloat(input1[i]), float16ToFloat(input2[i])));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.MAX_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.MAX_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorMaxFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.MAX,
                                     Float16Vector.fromArray(SPECIES, input2, i))
                           .intoArray(output, i);
        }
        for (; i < LEN; i++) {
            output[i] = float16ToRawShortBits(
                            max(shortBitsToFloat16(input1[i]),
                                shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMaxFloat16")
    void checkResultMax() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.max(float16ToFloat(input1[i]), float16ToFloat(input2[i])));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.SQRT_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.SQRT_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorSqrtFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.SQRT)
                           .intoArray(output, i);
        }
        for (; i < LEN; i++) {
            output[i] = float16ToRawShortBits(shortBitsToFloat16(input1[i]));
        }
    }

    @Check(test="vectorSqrtFloat16")
    void checkResultSqrt() {
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(sqrt(shortBitsToFloat16(input1[i])));
            assertResults(1, input1[i], expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorFmaFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.FMA,
                                     Float16Vector.fromArray(SPECIES, input2, i),
                                     Float16Vector.fromArray(SPECIES, input3, i))
                           .intoArray(output, i);
        }
        for (; i < LEN; i++) {
            output[i] = float16ToRawShortBits(
                            fma(shortBitsToFloat16(input1[i]),
                                shortBitsToFloat16(input2[i]),
                                shortBitsToFloat16(input3[i])));
        }
    }

    @Check(test="vectorFmaFloat16")
    void checkResultFma() {
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i]),
                                                       shortBitsToFloat16(input3[i])));
            assertResults(3, input1[i], input2[i], input3[i], expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorFmaFloat16ScalarMixedConstants() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.FMA,
                                     FP16_SCALAR,
                                     floatToFloat16(3.0f))
                           .intoArray(output, i);
        }
        for (; i < LEN; i++) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]),
                                                  shortBitsToFloat16(FP16_SCALAR),
                                                  shortBitsToFloat16(floatToFloat16(3.0f))));
        }
    }

    @Check(test="vectorFmaFloat16ScalarMixedConstants")
    void checkResultFmaScalarMixedConstants() {
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(FP16_SCALAR),
                                                       shortBitsToFloat16(floatToFloat16(3.0f))));
            assertResults(2, input1[i], FP16_SCALAR, expected, output[i]);
        }
    }


    @Test
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorFmaFloat16MixedConstants() {
        short input3 = floatToFloat16(3.0f);
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.FMA,
                                     Float16Vector.fromArray(SPECIES, input2, i),
                                     input3)
                           .intoArray(output, i);
        }
        for (; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]),
                                                  shortBitsToFloat16(input2[i]),
                                                  shortBitsToFloat16(input3)));
        }
    }


    @Check(test="vectorFmaFloat16MixedConstants")
    void checkResultFmaMixedConstants() {
        short input3 = floatToFloat16(3.0f);
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i]), shortBitsToFloat16(input3)));
            assertResults(3, input1[i], input2[i], input3, expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorFmaFloat16AllConstants() {
        short input1 = floatToFloat16(1.0f);
        short input2 = floatToFloat16(2.0f);
        short input3 = floatToFloat16(3.0f);
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.broadcast(SPECIES, input1)
                           .lanewise(VectorOperators.FMA,
                                     input2,
                                     input3)
                           .intoArray(output, i);
        }
        for (; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(input1),
                                                  shortBitsToFloat16(input2),
                                                  shortBitsToFloat16(input3)));
        }
    }

    @Check(test="vectorFmaFloat16AllConstants")
    void checkResultFmaAllConstants() {
        short input1 = floatToFloat16(1.0f);
        short input2 = floatToFloat16(2.0f);
        short input3 = floatToFloat16(3.0f);
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(fma(shortBitsToFloat16(input1), shortBitsToFloat16(input2), shortBitsToFloat16(input3)));
            assertResults(3, input1, input2, input3, expected, output[i]);
        }
    }


    @Test
    @IR(counts = {IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    void vectorAddConstInputFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.ADD,
                                     float16ToRawShortBits(FP16_CONST))
                           .intoArray(output, i);
        }
        for (; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(add(shortBitsToFloat16(input1[i]),
                                                  FP16_CONST));
        }
     }

    @Check(test="vectorAddConstInputFloat16")
    void checkResultAddConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) + FP16_CONST.floatValue());
            assertResults(2, input1[i], float16ToRawShortBits(FP16_CONST), expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.SUB_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorSubConstInputFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                           .lanewise(VectorOperators.SUB,
                                     float16ToRawShortBits(FP16_CONST))
                           .intoArray(output, i);
        }
        for (; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(subtract(shortBitsToFloat16(input1[i]),
                                                       FP16_CONST));
        }
    }

    @Check(test="vectorSubConstInputFloat16")
    void checkResultSubConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) - FP16_CONST.floatValue());
            assertResults(2, input1[i], float16ToRawShortBits(FP16_CONST), expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.MUL_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorMulConstantInputFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input2, i)
                           .lanewise(VectorOperators.MUL,
                                     float16ToRawShortBits(FP16_CONST))
                           .intoArray(output, i);
        }
        for (; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(multiply(FP16_CONST,
                                                       shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMulConstantInputFloat16")
    void checkResultMulConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(FP16_CONST.floatValue() * float16ToFloat(input2[i]));
            assertResults(2, float16ToRawShortBits(FP16_CONST), input2[i], expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.DIV_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorDivConstantInputFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input2, i)
                           .lanewise(VectorOperators.DIV,
                                     float16ToRawShortBits(FP16_CONST))
                           .intoArray(output, i);
        }
        for (; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(divide(shortBitsToFloat16(input2[i]),
                                                     FP16_CONST));
        }
    }

    @Check(test="vectorDivConstantInputFloat16")
    void checkResultDivConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input2[i]) / FP16_CONST.floatValue());
            assertResults(2, input2[i], float16ToRawShortBits(FP16_CONST), expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.MAX_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorMaxConstantInputFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input2, i)
                           .lanewise(VectorOperators.MAX,
                                     float16ToRawShortBits(FP16_CONST))
                           .intoArray(output, i);
        }
        for (; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(max(FP16_CONST,
                                                  shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMaxConstantInputFloat16")
    void checkResultMaxConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.max(FP16_CONST.floatValue(), float16ToFloat(input2[i])));
            assertResults(2, float16ToRawShortBits(FP16_CONST), input2[i], expected, output[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorMinConstantInputFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input2, i)
                           .lanewise(VectorOperators.MIN,
                                     float16ToRawShortBits(FP16_CONST))
                           .intoArray(output, i);
        }
        for (; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(min(FP16_CONST,
                                                  shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMinConstantInputFloat16")
    void checkResultMinConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.min(FP16_CONST.floatValue(), float16ToFloat(input2[i])));
            assertResults(2, float16ToRawShortBits(FP16_CONST), input2[i], expected, output[i]);
        }
    }
}
