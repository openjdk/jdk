/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
* @bug 8308363 8336406
* @summary Validate compiler IR for FP16 scalar operations.
* @requires vm.compiler2.enabled
* @library /test/lib /
* @run driver TestFP16ScalarOps
*/

import compiler.lib.ir_framework.*;
import java.util.Random;
import static java.lang.Float16.*;

public class TestFP16ScalarOps {
    private static final int count = 1024;

    private short[] src;
    private short[] dst;
    private short res;

    public static void main(String args[]) {
        TestFramework.runWithFlags("--enable-preview");
    }

    public TestFP16ScalarOps() {
        src = new short[count];
        dst = new short[count];
        for (int i = 0; i < count; i++) {
            src[i] = Float.floatToFloat16(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.ADD_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testAdd1() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.add(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(failOn = {IRNode.ADD_HF, IRNode.REINTERPRET_S2HF, IRNode.REINTERPRET_HF2S},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(failOn = {IRNode.ADD_HF, IRNode.REINTERPRET_S2HF, IRNode.REINTERPRET_HF2S},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testAdd2() {
        Float16 hf0 = shortBitsToFloat16((short)0);
        Float16 hf1 = shortBitsToFloat16((short)15360);
        Float16 hf2 = shortBitsToFloat16((short)16384);
        Float16 hf3 = shortBitsToFloat16((short)16896);
        Float16 hf4 = shortBitsToFloat16((short)17408);
        res = float16ToRawShortBits(Float16.add(Float16.add(Float16.add(Float16.add(hf0, hf1), hf2), hf3), hf4));
    }

    @Test
    @IR(counts = {IRNode.SUB_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.SUB_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testSub() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.subtract(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.MUL_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.MUL_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testMul() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.multiply(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.DIV_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.DIV_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testDiv() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.divide(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.MAX_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.MAX_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testMax() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.max(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.MIN_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testMin() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.min(res, shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.ABS_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testAbs() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.abs(shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.SQRT_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.SQRT_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testSqrt() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            res = Float16.sqrt(shortBitsToFloat16(src[i]));
            dst[i] = float16ToRawShortBits(res);
        }
    }

    @Test
    @IR(counts = {IRNode.FMA_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.FMA_HF, "> 0", IRNode.REINTERPRET_S2HF, "> 0", IRNode.REINTERPRET_HF2S, "> 0"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void testFma() {
        Float16 res = shortBitsToFloat16((short)0);
        for (int i = 0; i < count; i++) {
            Float16 in = shortBitsToFloat16(src[i]);
            res = Float16.fma(in, in, in) ;
            dst[i] = float16ToRawShortBits(res);
        }
    }
}
