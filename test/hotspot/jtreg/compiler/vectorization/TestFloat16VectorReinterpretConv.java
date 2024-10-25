/*
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
* @bug 8330021
* @summary Test auto-vectorization for "dst (ConvHF2F (ReinterpretHF2S src))" sequence
* @modules jdk.incubator.vector
* @requires vm.compiler2.enabled
* @library /test/lib /
* @run driver compiler.vectorization.TestFloat16VectorReinterpretConv
*/

package compiler.vectorization;
import compiler.lib.ir_framework.*;
import java.util.Random;
import static jdk.incubator.vector.Float16.*;
import static java.lang.Float.*;

public class TestFloat16VectorReinterpretConv {
    private short[] fin;
    private float[] flout;
    private static final int LEN = 2048;
    private Random rng;

    public static void main(String args[]) {
        TestFramework.runWithFlags("-XX:-TieredCompilation", "-Xbatch", "--add-modules=jdk.incubator.vector");
    }

    public TestFloat16VectorReinterpretConv() {
        fin  = new short[LEN];
        flout = new float[LEN];
        rng = new Random(25);
        for (int i = 0; i < LEN; i++) {
            fin[i] = floatToFloat16(rng.nextFloat());
        }
    }

    // When auto-vectorizing a sequence like - "dst (ConvHF2F (ReinterpretHF2S src))", the compilation
    // fails due to an assertion error when testing for the source type in vectorCastNode::opcode() for
    // Op_ConvHF2F. The input passed to ConvHF2F is of type T_INT but is supposed to be of type T_SHORT. It is
    // because the container type for ReinterpretHF2S is computed as T_INT instead of T_SHORT. Fix for this
    // is part of JDK-8330021 and this test makes sure the compilation does not fail and vectorization of both
    // ConvHF2F and ReinterpretHF2S takes place.
    @Test
    @Warmup(10000)
    @IR(counts = {IRNode.VECTOR_CAST_HF2F, " >= 1", IRNode.VECTOR_REINTERPRET, " >= 1"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "sve", "true"})
    @IR(counts = {IRNode.VECTOR_CAST_HF2F, " >= 1", IRNode.VECTOR_REINTERPRET, " >= 1"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testVect() {
        for (int i = 0; i < LEN; i++) {
            flout[i] = float16ToFloat(float16ToRawShortBits(add(shortBitsToFloat16(fin[i]), shortBitsToFloat16(fin[i]))));
        }
    }

    @Check(test="testVect")
    public void checkResult() {
        for (int i = 0; i < LEN; i++) {
            float expected = float16ToFloat(fin[i]) + float16ToFloat(fin[i]);
            if (flout[i] != expected) {
                throw new RuntimeException("Invalid result: flout[" + i + "] = " + flout[i] + " != " + expected);
            }
        }
    }
}
