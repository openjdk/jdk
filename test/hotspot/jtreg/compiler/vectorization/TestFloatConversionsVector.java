/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8294588
 * @summary Auto-vectorize Float.floatToFloat16, Float.float16ToFloat APIs
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.vectorization.TestFloatConversionsVector nCOH_nAV
 * @run driver compiler.vectorization.TestFloatConversionsVector nCOH_yAV
 * @run driver compiler.vectorization.TestFloatConversionsVector yCOH_nAV
 * @run driver compiler.vectorization.TestFloatConversionsVector yCOH_yAV
 */

package compiler.vectorization;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestFloatConversionsVector {
    private static final int ARRLEN = 1024;
    private static final int ITERS  = 11000;
    private static float  [] finp;
    private static short  [] sout;
    private static short  [] sinp;
    private static float  [] fout;

    public static void main(String args[]) {
        TestFramework framework = new TestFramework(TestFloatConversionsVector.class);
        framework.addFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3");
        switch (args[0]) {
            case "nCOH_nAV" -> { framework.addFlags("-XX:-UseCompactObjectHeaders", "-XX:-AlignVector"); }
            case "nCOH_yAV" -> { framework.addFlags("-XX:-UseCompactObjectHeaders", "-XX:+AlignVector"); }
            case "yCOH_nAV" -> { framework.addFlags("-XX:+UseCompactObjectHeaders", "-XX:-AlignVector"); }
            case "yCOH_yAV" -> { framework.addFlags("-XX:+UseCompactObjectHeaders", "-XX:+AlignVector"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
        System.out.println("PASSED");
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_F2HF, IRNode.VECTOR_SIZE + "min(max_float, max_short)", "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfCPUFeatureOr = {"f16c", "true", "avx512f", "true", "zvfh", "true", "asimd", "true", "sve", "true"})
    public void test_float_float16(short[] sout, float[] finp) {
        for (int i = 0; i < finp.length; i++) {
            sout[i] = Float.floatToFloat16(finp[i]);
            // With AlignVector, we need 8-byte alignment of vector loads/stores.
            // UseCompactObjectHeaders=false                 UseCompactObjectHeaders=true
            // F_adr = base + 16 + 4*i   ->  i % 2 = 0       F_adr = base + 12 + 4*i   ->  i % 2 = 1
            // S_adr = base + 16 + 2*i   ->  i % 4 = 0       S_adr = base + 12 + 2*i   ->  i % 4 = 2
            // -> vectorize                                  -> no vectorization
        }
    }

    @Test
    public void test_float_float16_strided(short[] sout, float[] finp) {
        for (int i = 0; i < finp.length/2; i++) {
            sout[i*2] = Float.floatToFloat16(finp[i*2]);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_F2HF, IRNode.VECTOR_SIZE_2, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfCPUFeature = {"asimd", "true"})
    public void test_float_float16_short_vector(short[] sout, float[] finp) {
        for (int i = 0; i < finp.length; i += 4) {
            sout[i] = Float.floatToFloat16(finp[i]);
            sout[i + 1] = Float.floatToFloat16(finp[i + 1]);
        }
    }

    @Run(test = {"test_float_float16", "test_float_float16_strided",
                 "test_float_float16_short_vector"}, mode = RunMode.STANDALONE)
    public void kernel_test_float_float16() {
        finp = new float[ARRLEN];
        sout = new short[ARRLEN];

        for (int i = 0; i < ARRLEN; i++) {
            finp[i] = (float) i * 1.4f;
        }

        for (int i = 0; i < ITERS; i++) {
            test_float_float16(sout, finp);
        }

        // Verifying the result
        for (int i = 0; i < ARRLEN; i++) {
            Asserts.assertEquals(Float.floatToFloat16(finp[i]), sout[i]);
        }

        for (int i = 0; i < ITERS; i++) {
            test_float_float16_strided(sout, finp);
        }

        // Verifying the result
        for (int i = 0; i < ARRLEN/2; i++) {
            Asserts.assertEquals(Float.floatToFloat16(finp[i*2]), sout[i*2]);
        }

        for (int i = 0; i < ITERS; i++) {
            test_float_float16_short_vector(sout, finp);
        }

        // Verifying the result
        for (int i = 0; i < ARRLEN; i += 4) {
            Asserts.assertEquals(Float.floatToFloat16(finp[i]), sout[i]);
            Asserts.assertEquals(Float.floatToFloat16(finp[i + 1]), sout[i + 1]);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_HF2F, IRNode.VECTOR_SIZE + "min(max_float, max_short)", "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfCPUFeatureOr = {"f16c", "true", "avx512f", "true", "zvfh", "true", "asimd", "true", "sve", "true"})
    public void test_float16_float(float[] fout, short[] sinp) {
        for (int i = 0; i < sinp.length; i++) {
            fout[i] = Float.float16ToFloat(sinp[i]);
            // With AlignVector, we need 8-byte alignment of vector loads/stores.
            // UseCompactObjectHeaders=false                 UseCompactObjectHeaders=true
            // F_adr = base + 16 + 4*i   ->  i % 2 = 0       F_adr = base + 12 + 4*i   ->  i % 2 = 1
            // S_adr = base + 16 + 2*i   ->  i % 4 = 0       S_adr = base + 12 + 2*i   ->  i % 4 = 2
            // -> vectorize                                  -> no vectorization
        }
    }

    @Test
    public void test_float16_float_strided(float[] fout, short[] sinp) {
        for (int i = 0; i < sinp.length/2; i++) {
            fout[i*2] = Float.float16ToFloat(sinp[i*2]);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_HF2F, IRNode.VECTOR_SIZE_2, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfCPUFeature = {"asimd", "true"})
    public void test_float16_float_short_vector(float[] fout, short[] sinp) {
        for (int i = 0; i < sinp.length; i += 4) {
            fout[i] = Float.float16ToFloat(sinp[i]);
            fout[i + 1] = Float.float16ToFloat(sinp[i + 1]);
        }
    }

    @Run(test = {"test_float16_float", "test_float16_float_strided",
                 "test_float16_float_short_vector"}, mode = RunMode.STANDALONE)
    public void kernel_test_float16_float() {
        sinp = new short[ARRLEN];
        fout = new float[ARRLEN];

        for (int i = 0; i < ARRLEN; i++) {
            sinp[i] = (short)i;
        }

        for (int i = 0; i < ITERS; i++) {
            test_float16_float(fout, sinp);
        }

        // Verifying the result
        for (int i = 0; i < ARRLEN; i++) {
            Asserts.assertEquals(Float.float16ToFloat(sinp[i]), fout[i]);
        }

        for (int i = 0; i < ITERS; i++) {
            test_float16_float_strided(fout, sinp);
        }

        // Verifying the result
        for (int i = 0; i < ARRLEN/2; i++) {
            Asserts.assertEquals(Float.float16ToFloat(sinp[i*2]), fout[i*2]);
        }

        for (int i = 0; i < ITERS; i++) {
            test_float16_float_short_vector(fout, sinp);
        }

        // Verifying the result
        for (int i = 0; i < ARRLEN; i += 4) {
            Asserts.assertEquals(Float.float16ToFloat(sinp[i]), fout[i]);
            Asserts.assertEquals(Float.float16ToFloat(sinp[i + 1]), fout[i + 1]);
        }
    }
}
