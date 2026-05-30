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
package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.*;

import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

/*
 * @test
 * @bug 8382523
 * @summary Optimize Float16 to integral conversion operations for AVX512-FP16 targets.
 * @modules jdk.incubator.vector
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestFloat16ToIntegralConv
 */
public class TestFloat16ToIntegralConv {
    private static final int SIZE = 1024;

    private short[] fp16inp;

    private int[] goldenI;
    private long[] goldenL;
    private short[] goldenS;
    private byte[] goldenB;

    private int[] vecIout;
    private long[] vecLout;
    private short[] vecSout;
    private byte[] vecBout;

    private static Generator<Short> genHF = G.float16s();

    public TestFloat16ToIntegralConv() {
        fp16inp = new short[SIZE];
        goldenI = new int[SIZE];
        goldenL = new long[SIZE];
        goldenS = new short[SIZE];
        goldenB = new byte[SIZE];
        vecIout = new int[SIZE];
        vecLout = new long[SIZE];
        vecSout = new short[SIZE];
        vecBout = new byte[SIZE];

        G.fill(genHF, fp16inp);

        for (int i = 0; i < SIZE; i++) {
            float fval = Float.float16ToFloat(fp16inp[i]);
            goldenI[i] = (int) fval;
            goldenL[i] = (long) fval;
            goldenS[i] = (short) fval;
            goldenB[i] = (byte) fval;
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    // ======================== Scalar tests ========================

    @Test
    @IR(counts = {IRNode.X86_SCONV_HF2I, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public int testConvHF2I(int idx) {
        return (int) Float.float16ToFloat(fp16inp[idx]);
    }

    @Run(test = "testConvHF2I")
    public void runConvHF2I() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(testConvHF2I(i), goldenI[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_SCONV_HF2L, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public long testConvHF2L(int idx) {
        return (long) Float.float16ToFloat(fp16inp[idx]);
    }

    @Run(test = "testConvHF2L")
    public void runConvHF2L() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(testConvHF2L(i), goldenL[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_SCONV_HF2I, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public short testConvHF2S(int idx) {
        return (short) Float.float16ToFloat(fp16inp[idx]);
    }

    @Run(test = "testConvHF2S")
    public void runConvHF2S() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(testConvHF2S(i), goldenS[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_SCONV_HF2I, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public byte testConvHF2B(int idx) {
        return (byte) Float.float16ToFloat(fp16inp[idx]);
    }

    @Run(test = "testConvHF2B")
    public void runConvHF2B() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(testConvHF2B(i), goldenB[i]);
        }
    }

    // ======================== Scalar VAPI tests ========================

    @Test
    @IR(counts = {IRNode.X86_SCONV_HF2I, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public int testConvHF2I_VAPI(int idx) {
        return shortBitsToFloat16(fp16inp[idx]).intValue();
    }

    @Run(test = "testConvHF2I_VAPI")
    public void runConvHF2I_VAPI() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(testConvHF2I_VAPI(i), goldenI[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_SCONV_HF2L, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public long testConvHF2L_VAPI(int idx) {
        return shortBitsToFloat16(fp16inp[idx]).longValue();
    }

    @Run(test = "testConvHF2L_VAPI")
    public void runConvHF2L_VAPI() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(testConvHF2L_VAPI(i), goldenL[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_SCONV_HF2I, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public short testConvHF2S_VAPI(int idx) {
        return shortBitsToFloat16(fp16inp[idx]).shortValue();
    }

    @Run(test = "testConvHF2S_VAPI")
    public void runConvHF2S_VAPI() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(testConvHF2S_VAPI(i), goldenS[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_SCONV_HF2I, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public byte testConvHF2B_VAPI(int idx) {
        return shortBitsToFloat16(fp16inp[idx]).byteValue();
    }

    @Run(test = "testConvHF2B_VAPI")
    public void runConvHF2B_VAPI() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(testConvHF2B_VAPI(i), goldenB[i]);
        }
    }

    // ======================== Vector tests ========================

    @Test
    @IR(counts = {IRNode.X86_VCAST_HF2X, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void testVecConvHF2I() {
        for (int i = 0; i < SIZE; i++) {
            vecIout[i] = (int) Float.float16ToFloat(fp16inp[i]);
        }
    }

    @Run(test = "testVecConvHF2I")
    public void runVecConvHF2I() {
        testVecConvHF2I();
        Verify.checkEQ(vecIout, goldenI);
    }

    @Test
    @IR(counts = {IRNode.X86_VCAST_HF2X, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void testVecConvHF2L() {
        for (int i = 0; i < SIZE; i++) {
            vecLout[i] = (long) Float.float16ToFloat(fp16inp[i]);
        }
    }

    @Run(test = "testVecConvHF2L")
    public void runVecConvHF2L() {
        testVecConvHF2L();
        Verify.checkEQ(vecLout, goldenL);
    }

    @Test
    @IR(counts = {IRNode.X86_VCAST_HF2X, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void testVecConvHF2S() {
        for (int i = 0; i < SIZE; i++) {
            vecSout[i] = (short) Float.float16ToFloat(fp16inp[i]);
        }
    }

    @Run(test = "testVecConvHF2S")
    public void runVecConvHF2S() {
        testVecConvHF2S();
        Verify.checkEQ(vecSout, goldenS);
    }

    @Test
    @IR(counts = {IRNode.X86_VCAST_HF2X, "> 0"}, phase = CompilePhase.FINAL_CODE,
        applyIfCPUFeature = {"avx512_fp16", "true"})
    public void testVecConvHF2B() {
        for (int i = 0; i < SIZE; i++) {
            vecBout[i] = (byte) Float.float16ToFloat(fp16inp[i]);
        }
    }

    @Run(test = "testVecConvHF2B")
    public void runVecConvHF2B() {
        testVecConvHF2B();
        Verify.checkEQ(vecBout, goldenB);
    }
}
