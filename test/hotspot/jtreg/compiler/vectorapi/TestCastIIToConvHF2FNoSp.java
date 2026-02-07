/*
 * Copyright 2025 Arm Limited and/or its affiliates.
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
 *
 */

/**
* @test
* @bug 8373574
* @summary Verify correct execution of CastII -> ConvHF2F IR sequence on AArch64
* @modules jdk.incubator.vector
* @library /test/lib /
* @compile TestCastIIToConvHF2FNoSp.java
* @run driver/timeout=480 compiler.vectorapi.TestCastIIToConvHF2FNoSp
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

public class TestCastIIToConvHF2FNoSp {
    short[] input1;
    short[] output;
    static final int LEN = 527;

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

    public TestCastIIToConvHF2FNoSp() {
        input1 = new short[LEN];
        output = new short[LEN];

        Generator<Short> gen = G.float16s();
        for (int i = 0; i < LEN; ++i) {
            input1[i] = gen.next();
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_VHF, " >0 "},
        applyIfCPUFeature = {"sve", "true"})
    @IR(counts = {IRNode.MIN_VHF, " >0 "},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true", "sve", "false"})
    void vectorMinConstantInputFloat16() {
        int i = 0;
        for (; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                         .lanewise(VectorOperators.MIN,
                                   float16ToRawShortBits(FP16_CONST))
                         .intoArray(output, i);
        }
        if (i < LEN) {
            VectorMask<Float16> mask = SPECIES.indexInRange(i, LEN);
            Float16Vector.fromArray(SPECIES, input1, i, mask)
                         .lanewise(VectorOperators.MIN,
                                   float16ToRawShortBits(FP16_CONST))
                         .intoArray(output, i, mask);
        }
    }

    @Check(test="vectorMinConstantInputFloat16")
    void checkResultMinConstantInputFloat16() {
        for (int i = 0; i < LEN; ++i) {
            short expected = floatToFloat16(Math.min(FP16_CONST.floatValue(), float16ToFloat(input1[i])));
            assertResults(2, float16ToRawShortBits(FP16_CONST), input1[i], expected, output[i]);
        }
    }
}
