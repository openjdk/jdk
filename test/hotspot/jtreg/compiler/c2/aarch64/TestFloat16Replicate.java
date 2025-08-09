/* Copyright (c) 2025, Arm Limited. All rights reserved.
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
* @bug 8361582
* @summary Ensure the correct backend replicate node is being generated for
*          half precision float constants on >=32B SVE machines
* @modules jdk.incubator.vector
* @library /test/lib /
* @run main/othervm compiler.c2.aarch64.TestFloat16Replicate
*/

package compiler.c2.aarch64;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import java.lang.Math;
import java.util.Arrays;
import java.util.stream.IntStream;
import jdk.incubator.vector.Float16;
import jdk.test.lib.*;

import static compiler.lib.generators.Generators.G;
import static java.lang.Float.*;
import static jdk.incubator.vector.Float16.*;

public class TestFloat16Replicate {
    private static short[] input;
    private static short[] output;

   // Choose FP16_CONST1 which is within the range of [-128 << 8, 127 << 8] and a multiple of 256
    private static final Float16 FP16_CONST1 = Float16.shortBitsToFloat16((short)512);

    // Choose a value out of the range of [-128 << 8, 127 << 8] or a non multiple of 256 for FP16_CONST2
    private static final Float16 FP16_CONST2 = Float16.shortBitsToFloat16((short)1035);

    private static final int LEN = 1024;

    public static void main(String args[]) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:-TieredCompilation");
    }

    static {
        input  = new short[LEN];
        output = new short[LEN];

        Generator<Short> gen = G.float16s();
        IntStream.range(0, LEN).forEach(i -> {input[i] = gen.next();});
    }

    // For a loop which is vectorizable and has an FP16 constant as one of the inputs, the (dst (Replicate con)) IR
    // will be generated. On SVE machines with vector length > 16B, the backend machnode - "replicateHF_imm_gt128b"
    // should be generated if the immediate is a signed value within the range [-128, 127] or a signed multiple of
    // 256 in the range [-32768, 32512] for element widths of 16 bits or higher
    @Test
    @Warmup(5000)
    @IR(counts = {IRNode.REPLICATE_HF_IMM8, ">0"},
        phase = CompilePhase.FINAL_CODE,
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfCPUFeature = {"sve", "true"})
    public void Float16AddConstInput1() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(Float16.add(shortBitsToFloat16(input[i]), FP16_CONST1));
        }
    }

    @Check(test="Float16AddConstInput1")
    public void checkResultFloat16AddConstInput1() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input[i]) + FP16_CONST1.floatValue());
            if (expected != output[i]) {
                throw new AssertionError("Result Mismatch!, input = " + input[i] + " constant = " + FP16_CONST1 + " actual = " + output[i] +  " expected = " + expected);
            }
        }
    }

    // For a loop which is vectorizable and has an FP16 constant as one of the inputs, the (dst (Replicate con)) IR
    // will be generated. On SVE machines with vector length > 16B, the backend machnode - "replicateHF" should be
    // generated in cases where the immediate falls out of the permissible range of values that are acceptable by the
    // SVE "dup" instruction. This results in loading the FP16 constant from the constant pool which is then broadcasted
    // to an SVE register for further operations. The backend machnode - "replicateHF_imm8_gt128b" should not be
    // generated.
    @Test
    @Warmup(5000)
    @IR(counts = {IRNode.REPLICATE_HF, ">0"},
        failOn = {IRNode.REPLICATE_HF_IMM8},
        phase = CompilePhase.FINAL_CODE,
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfCPUFeature = {"sve", "true"})
    public void Float16AddConstInput2() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(add(shortBitsToFloat16(input[i]), FP16_CONST2));
        }
    }

    @Check(test="Float16AddConstInput2")
    public void checkResultFloat16AddConstInput2() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(float16ToFloat(input[i]) + FP16_CONST2.floatValue());
            if (expected != output[i]) {
                throw new AssertionError("Result Mismatch!, input = " + input[i] + " constant = " + FP16_CONST2 + " actual = " + output[i] +  " expected = " + expected);
            }
        }
    }
}
