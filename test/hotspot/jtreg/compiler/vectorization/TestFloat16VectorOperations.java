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
* @bug 8346236
* @summary Auto-vectorization support for various Float16 operations
* @modules jdk.incubator.vector
* @library /test/lib /
* @compile TestFloat16VectorOperations.java
* @run driver compiler.vectorization.TestFloat16VectorOperations
*/

package compiler.vectorization;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.*;
import static java.lang.Float.*;
import java.util.Arrays;
import jdk.test.lib.*;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

public class TestFloat16VectorOperations {
    private short[] input1;
    private short[] input2;
    private short[] input3;
    private short[] output;
    private static short FP16_SCALAR = (short)0x7777;
    private static final int LEN = 2048;

    private static final Float16 FP16_CONST = Float16.valueOf(1023.0f);

    public static void main(String args[]) {
        // Test with default MaxVectorSize
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");

        // Test with different values of MaxVectorSize
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:MaxVectorSize=8");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:MaxVectorSize=16");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:MaxVectorSize=32");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:MaxVectorSize=64");
    }

    public static void assertResults(int arity, short ... values) {
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
    @Warmup(50)
    @IR(counts = {IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorAddFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(add(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorAddFloat16")
    public void checkResultAdd() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) + float16ToFloat(input2[i]));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }


    @Test
    @Warmup(50)
    @IR(counts = {IRNode.SUB_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.SUB_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorSubFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(subtract(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorSubFloat16")
    public void checkResultSub() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) - float16ToFloat(input2[i]));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }


    @Test
    @Warmup(50)
    @IR(counts = {IRNode.MUL_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.MUL_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorMulFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(multiply(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMulFloat16")
    public void checkResultMul() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) * float16ToFloat(input2[i]));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.DIV_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.DIV_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorDivFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(divide(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorDivFloat16")
    public void checkResultDiv() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) / float16ToFloat(input2[i]));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.MIN_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.MIN_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorMinFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(min(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMinFloat16")
    public void checkResultMin() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.min(float16ToFloat(input1[i]), float16ToFloat(input2[i])));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.MAX_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.MAX_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorMaxFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(max(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMaxFloat16")
    public void checkResultMax() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.max(float16ToFloat(input1[i]), float16ToFloat(input2[i])));
            assertResults(2, input1[i], input2[i], expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.SQRT_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.SQRT_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorSqrtFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(sqrt(shortBitsToFloat16(input1[i])));
        }
    }

    @Check(test="vectorSqrtFloat16")
    public void checkResultSqrt() {
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(sqrt(shortBitsToFloat16(input1[i])));
            assertResults(1, input1[i], expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorFmaFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i]),
                                                  shortBitsToFloat16(input3[i])));
        }
    }

    @Check(test="vectorFmaFloat16")
    public void checkResultFma() {
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i]),
                                                       shortBitsToFloat16(input3[i])));
            assertResults(3, input1[i], input2[i], input3[i], expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorFmaFloat16ScalarMixedConstants() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(FP16_SCALAR),
                                                  shortBitsToFloat16(floatToFloat16(3.0f))));
        }
    }

    @Check(test="vectorFmaFloat16ScalarMixedConstants")
    public void checkResultFmaScalarMixedConstants() {
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(FP16_SCALAR),
                                                       shortBitsToFloat16(floatToFloat16(3.0f))));
            assertResults(2, input1[i], FP16_SCALAR, expected, output[i]);
        }
    }


    @Test
    @Warmup(50)
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorFmaFloat16MixedConstants() {
        short input3 = floatToFloat16(3.0f);
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i]), shortBitsToFloat16(input3)));
        }
    }


    @Check(test="vectorFmaFloat16MixedConstants")
    public void checkResultFmaMixedConstants() {
        short input3 = floatToFloat16(3.0f);
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i]), shortBitsToFloat16(input3)));
            assertResults(3, input1[i], input2[i], input3, expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.FMA_VHF, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorFmaFloat16AllConstants() {
        short input1 = floatToFloat16(1.0f);
        short input2 = floatToFloat16(2.0f);
        short input3 = floatToFloat16(3.0f);
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(input1), shortBitsToFloat16(input2), shortBitsToFloat16(input3)));
        }
    }

    @Check(test="vectorFmaFloat16AllConstants")
    public void checkResultFmaAllConstants() {
        short input1 = floatToFloat16(1.0f);
        short input2 = floatToFloat16(2.0f);
        short input3 = floatToFloat16(3.0f);
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(fma(shortBitsToFloat16(input1), shortBitsToFloat16(input2), shortBitsToFloat16(input3)));
            assertResults(3, input1, input2, input3, expected, output[i]);
        }
    }


    @Test
    @Warmup(50)
    @IR(counts = {IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zvfh", "true", "sve", "true"})
    @IR(counts = {IRNode.ADD_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorAddConstInputFloat16() {
         for (int i = 0; i < LEN; ++i) {
             output[i] = float16ToRawShortBits(add(shortBitsToFloat16(input1[i]), FP16_CONST));
         }
     }

    @Check(test="vectorAddConstInputFloat16")
    public void checkResultAddConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) + FP16_CONST.floatValue());
            assertResults(2, input1[i], float16ToRawShortBits(FP16_CONST), expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.SUB_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void vectorSubConstInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(subtract(shortBitsToFloat16(input1[i]), FP16_CONST));
        }
    }

    @Check(test="vectorSubConstInputFloat16")
    public void checkResultSubConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input1[i]) - FP16_CONST.floatValue());
            assertResults(2, input1[i], float16ToRawShortBits(FP16_CONST), expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.MUL_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void vectorMulConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(multiply(FP16_CONST, shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMulConstantInputFloat16")
    public void checkResultMulConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(FP16_CONST.floatValue() * float16ToFloat(input2[i]));
            assertResults(2, float16ToRawShortBits(FP16_CONST), input2[i], expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.DIV_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void vectorDivConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(divide(FP16_CONST, shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorDivConstantInputFloat16")
    public void checkResultDivConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(FP16_CONST.floatValue() / float16ToFloat(input2[i]));
            assertResults(2, float16ToRawShortBits(FP16_CONST), input2[i], expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.MAX_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void vectorMaxConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(max(FP16_CONST, shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMaxConstantInputFloat16")
    public void checkResultMaxConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.max(FP16_CONST.floatValue(), float16ToFloat(input2[i])));
            assertResults(2, float16ToRawShortBits(FP16_CONST), input2[i], expected, output[i]);
        }
    }

    @Test
    @Warmup(50)
    @IR(counts = {IRNode.MIN_VHF, " >0 "},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void vectorMinConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(min(FP16_CONST, shortBitsToFloat16(input2[i])));
        }
    }

    @Check(test="vectorMinConstantInputFloat16")
    public void checkResultMinConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.min(FP16_CONST.floatValue(), float16ToFloat(input2[i])));
            assertResults(2, float16ToRawShortBits(FP16_CONST), input2[i], expected, output[i]);
        }
    }
}
