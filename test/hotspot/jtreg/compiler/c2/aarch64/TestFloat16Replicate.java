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
* @key randomness
* @summary Ensure the correct backend replicate node is being generated for
*          half precision float constants on >16B SVE machines
* @modules jdk.incubator.vector
* @library /test/lib /
* @run main/othervm compiler.c2.aarch64.TestFloat16Replicate
*/

package compiler.c2.aarch64;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import java.util.Arrays;
import java.util.Random;
import jdk.incubator.vector.Float16;
import jdk.test.lib.*;
import jdk.test.lib.Utils;

import static java.lang.Float.*;
import static jdk.incubator.vector.Float16.*;

public class TestFloat16Replicate {
    private static short[] input;
    private static short[] output;
    private static short[] expected;
    private static Random rnd;

    // Choose FP16_IMM8 which is within the range of [-128 << 8, 127 << 8] and a multiple of 256
    private static final Float16 FP16_IMM8;

    // Choose a value in the range [-128 << 8, 127 << 8] and a non multiple of 256 for FP16_NON_IMM8
    private static final Float16 FP16_NON_IMM8;

    private static final int LEN = 1024;

    public static void main(String args[]) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:-TieredCompilation");
    }

    static {
        rnd = Utils.getRandomInstance();
        int k = rnd.nextInt(-128, 128);
        int b = rnd.nextInt(1, 256);
        short bits_imm8     = (short) (k << 8);
        short bits_non_imm8 = (short) ((k << 8) + b);

        FP16_IMM8     = Float16.shortBitsToFloat16(bits_imm8);
        FP16_NON_IMM8 = Float16.shortBitsToFloat16(bits_non_imm8);

        input  = new short[LEN];
        output = new short[LEN];
        expected = new short[LEN];

        for (int i = 0; i < LEN; i++) {
            input[i] = (short) i;
        }
    }

    // For vectorizable loops containing FP16 operations with an FP16 constant as one of the inputs, the IR
    // node `(dst (Replicate con))` is generated to broadcast the constant into all lanes of an SVE register.
    // On SVE-capable hardware with vector length > 16B, if the FP16 immediate is a signed value within the
    // range [-128, 127] or a signed multiple of 256 in the range [-32768, 32512] for element widths of
    // 16 bits or higher then the backend should generate the "replicateHF_imm_gt128b" machnode.
    @Test
    @Warmup(5000)
    @IR(counts = {IRNode.REPLICATE_HF_IMM8, ">0"},
        phase = CompilePhase.FINAL_CODE,
        applyIf = {"MaxVectorSize", ">16"},
        applyIfCPUFeature = {"sve", "true"})
    public void TestFloat16AddInRange() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(Float16.add(shortBitsToFloat16(input[i]), FP16_IMM8));
        }
    }

    @Check(test="TestFloat16AddInRange")
    public void checkResultFloat16AddInRange() {
        for (int i = 0; i < LEN; ++i) {
            expected[i] = floatToFloat16(float16ToFloat(input[i]) + FP16_IMM8.floatValue());
        }
        Verify.checkEQWithRawBits(output, expected);
    }

    // For vectorizable loops containing FP16 operations with an FP16 constant as one of the inputs, the IR
    // node `(dst (Replicate con))` is generated to broadcast the constant into all lanes of an SVE register.
    // On SVE-capable hardware with vector length > 16B, if the FP16 constant falls outside the immediate
    // range accepted by the SVE "dup" instruction, the backend must:
    //   1. Generate the "loadConH" machnode to load the FP16 constant from the constant pool.
    //   2. Emit the "replicateHF" machnode to broadcast this loaded constant into an SVE register.
    // In this case, the backend should not generate the "replicateHF_imm8_gt128b" machnode.
    @Test
    @Warmup(5000)
    @IR(counts = {IRNode.REPLICATE_HF, ">0"},
        failOn = {IRNode.REPLICATE_HF_IMM8},
        phase = CompilePhase.FINAL_CODE,
        applyIf = {"MaxVectorSize", ">16"},
        applyIfCPUFeature = {"sve", "true"})
    public void TestFloat16AddOutOfRange() {
        for (int i = 0; i < LEN; ++i) {
            output[i] = float16ToRawShortBits(add(shortBitsToFloat16(input[i]), FP16_NON_IMM8));
        }
    }

    @Check(test="TestFloat16AddOutOfRange")
    public void checkResultFloat16AddOutOfRange() {
        for (int i = 0; i < LEN; ++i) {
            expected[i] = floatToFloat16(float16ToFloat(input[i]) + FP16_NON_IMM8.floatValue());
        }
        Verify.checkEQWithRawBits(output, expected);
    }
}
