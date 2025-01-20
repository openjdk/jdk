/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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
* @bug 8342103
* @summary Auto-vectorization support for various Float16 operations
* @requires vm.compiler2.enabled
* @modules jdk.incubator.vector
* @library /test/lib /
* @compile TestFloat16VectorOperations.java
* @run driver compiler.vectorization.TestFloat16VectorOperations
*/

package compiler.vectorization;
import compiler.lib.ir_framework.*;
import java.util.Random;
import static jdk.incubator.vector.Float16.*;
import static java.lang.Float.*;

public class TestFloat16VectorOperations {
    private short[] input1;
    private short[] input2;
    private short[] input3;
    private short[] output;
    private static final int LEN = 2048;
    private Random rng;

    public static void main(String args[]) {
        TestFramework.runWithFlags("-XX:-TieredCompilation", "-Xbatch","--add-modules=jdk.incubator.vector");
    }

    public TestFloat16VectorOperations() {
        input1 = new short[LEN];
        input2 = new short[LEN];
        input3 = new short[LEN];
        output = new short[LEN];
        rng = new Random(42);
        for (int i = 0; i < LEN; ++i) {
            input1[i] = floatToFloat16(rng.nextFloat());
            input2[i] = floatToFloat16(rng.nextFloat());
            input3[i] = floatToFloat16(rng.nextFloat());
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.ADD_VHF, ">= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.ADD_VHF, ">= 1"},
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
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.SUB_VHF, ">= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.SUB_VHF, ">= 1"},
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
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.MUL_VHF, ">= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.MUL_VHF, ">= 1"},
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
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.DIV_VHF, ">= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.DIV_VHF, ">= 1"},
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
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.MIN_VHF, ">= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.MIN_VHF, ">= 1"},
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
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.MAX_VHF, ">= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.MAX_VHF, ">= 1"},
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
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.SQRT_VHF, ">= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.SQRT_VHF, ">= 1"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorSqrtFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(sqrt(shortBitsToFloat16(input1[i])));
        }
    }

    @Check(test="vectorSqrtFloat16")
    public void checkResultSqrt() {
        for (int i = 0; i < LEN; ++i) {
            short expected = float16ToRawShortBits(valueOf(Math.sqrt(float16ToFloat(input1[i]))));
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.FMA_VHF, ">= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, ">= 1"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorFmaFloat16() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i]), shortBitsToFloat16(input3[i])));
        }
    }

    @Check(test="vectorFmaFloat16")
    public void checkResultFma() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.fma(float16ToFloat(input1[i]), float16ToFloat(input2[i]), float16ToFloat(input3[i])));
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.FMA_VHF, " >= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, ">= 1"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorFmaFloat16MixedConstants() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(input1[i]), shortBitsToFloat16(input2[i]), shortBitsToFloat16(floatToFloat16(3.0f))));
        }
    }

    @Check(test="vectorFmaFloat16MixedConstants")
    public void checkResultFmaMixedConstants() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.fma(float16ToFloat(input1[i]), float16ToFloat(input2[i]), 3.0f));
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }

    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.FMA_VHF, " 0 "},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.FMA_VHF, " 0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void vectorFmaFloat16AllConstants() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(fma(shortBitsToFloat16(floatToFloat16(1.0f)), shortBitsToFloat16(floatToFloat16(2.0f)), shortBitsToFloat16(floatToFloat16(3.0f))));
        }
    }

    @Check(test="vectorFmaFloat16AllConstants")
    public void checkResultFmaAllConstants() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.fma(1.0f, 2.0f, 3.0f));
            if (output[i] != expected) {
                throw new RuntimeException("Invalid result: output[" + i + "] = " + output[i] + " != " + expected);
            }
        }
    }
}
