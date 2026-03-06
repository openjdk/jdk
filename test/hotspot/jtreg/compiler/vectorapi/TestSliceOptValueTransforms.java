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

/**
* @test
* @bug 8303762
* @summary Optimize vector slice operation with constant index using VPALIGNR instruction
* @modules jdk.incubator.vector
* @library /test/lib /
* @run main/othervm compiler.vectorapi.TestSliceOptValueTransforms
*/
package compiler.vectorapi;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import compiler.lib.generators.Generators.*;
import static compiler.lib.generators.Generators.G;

public class TestSliceOptValueTransforms {
    public static final int SIZE = 1024;

    public static final VectorSpecies<Byte> BSP = ByteVector.SPECIES_256;
    public static final VectorSpecies<Short> SSP = ShortVector.SPECIES_256;
    public static final VectorSpecies<Integer> ISP = IntVector.SPECIES_256;
    public static final VectorSpecies<Long> LSP = LongVector.SPECIES_256;
    public static final VectorSpecies<Float> FSP = FloatVector.SPECIES_256;
    public static final VectorSpecies<Double> DSP = DoubleVector.SPECIES_256;

    public static final VectorMask<Byte> bmask = VectorMask.fromLong(BSP, 0xF);
    public static final VectorMask<Short> smask = VectorMask.fromLong(SSP, 0xF);
    public static final VectorMask<Integer> imask = VectorMask.fromLong(ISP, 0xF);
    public static final VectorMask<Long> lmask = VectorMask.fromLong(LSP, 0xF);
    public static final VectorMask<Float> fmask = VectorMask.fromLong(FSP, 0xF);
    public static final VectorMask<Double> dmask = VectorMask.fromLong(DSP, 0x3);

    public static byte [] bsrc1;
    public static byte [] bsrc2;
    public static byte [] bdst;

    public static short [] ssrc1;
    public static short [] ssrc2;
    public static short [] sdst;

    public static int [] isrc1;
    public static int [] isrc2;
    public static int [] idst;

    public static long [] lsrc1;
    public static long [] lsrc2;
    public static long [] ldst;

    public static float [] fsrc1;
    public static float [] fsrc2;
    public static float [] fdst;

    public static double [] dsrc1;
    public static double [] dsrc2;
    public static double [] ddst;

    public static int var_slice_idx = 2;

    public TestSliceOptValueTransforms() {
        bsrc1 = new byte[SIZE];
        bsrc2 = new byte[SIZE];
        bdst  = new byte[SIZE];

        ssrc1 = new short[SIZE];
        ssrc2 = new short[SIZE];
        sdst  = new short[SIZE];

        isrc1 = new int[SIZE];
        isrc2 = new int[SIZE];
        idst  = new int[SIZE];

        lsrc1 = new long[SIZE];
        lsrc2 = new long[SIZE];
        ldst  = new long[SIZE];

        fsrc1 = new float[SIZE];
        fsrc2 = new float[SIZE];
        fdst  = new float[SIZE];

        dsrc1 = new double[SIZE];
        dsrc2 = new double[SIZE];
        ddst  = new double[SIZE];

        G.fill(G.ints(), isrc1);
        G.fill(G.ints(), isrc2);
        G.fill(G.longs(), lsrc1);
        G.fill(G.longs(), lsrc2);
        G.fill(G.floats(), fsrc1);
        G.fill(G.floats(), fsrc2);
        G.fill(G.doubles(), dsrc1);
        G.fill(G.doubles(), dsrc2);

        for (int i = 0; i < SIZE; i++) {
            bsrc1[i] = (byte)(isrc1[i]);
            bsrc2[i] = (byte)(isrc2[i]);

            ssrc1[i] = (short)(isrc1[i]);
            ssrc2[i] = (short)(isrc2[i]);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_B}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexByte() {
        for (int i = 0; i < BSP.loopBound(SIZE); i += BSP.length()) {
            ByteVector.fromArray(BSP, bsrc1, i)
                      .slice(0, ByteVector.fromArray(BSP, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_B}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexByte() {
        for (int i = 0; i < BSP.loopBound(SIZE); i += BSP.length()) {
            ByteVector.fromArray(BSP, bsrc1, i)
                      .slice(BSP.length(), ByteVector.fromArray(BSP, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_B, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexByte() {
        for (int i = 0; i < BSP.loopBound(SIZE); i += BSP.length()) {
            ByteVector.fromArray(BSP, bsrc1, i)
                      .slice(1, ByteVector.fromArray(BSP, bsrc2, i), bmask)
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index16B_reg", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexByte() {
        for (int i = 0; i < BSP.loopBound(SIZE); i += BSP.length()) {
            ByteVector.fromArray(BSP, bsrc1, i)
                      .slice(16, ByteVector.fromArray(BSP, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index_multiple4_reg_evex", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexByte() {
        for (int i = 0; i < BSP.loopBound(SIZE); i += BSP.length()) {
            ByteVector.fromArray(BSP, bsrc1, i)
                      .slice(8, ByteVector.fromArray(BSP, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_S}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexShort() {
        for (int i = 0; i < SSP.loopBound(SIZE); i += SSP.length()) {
            ShortVector.fromArray(SSP, ssrc1, i)
                       .slice(0, ShortVector.fromArray(SSP, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_S}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexShort() {
        for (int i = 0; i < SSP.loopBound(SIZE); i += SSP.length()) {
            ShortVector.fromArray(SSP, ssrc1, i)
                       .slice(SSP.length(), ShortVector.fromArray(SSP, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_S, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexShort() {
        for (int i = 0; i < SSP.loopBound(SIZE); i += SSP.length()) {
            ShortVector.fromArray(SSP, ssrc1, i)
                       .slice(1, ShortVector.fromArray(SSP, ssrc2, i), smask)
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index16B_reg", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexShort() {
        for (int i = 0; i < SSP.loopBound(SIZE); i += SSP.length()) {
            ShortVector.fromArray(SSP, ssrc1, i)
                       .slice(8, ShortVector.fromArray(SSP, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index_multiple4_reg_evex", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexShort() {
        for (int i = 0; i < SSP.loopBound(SIZE); i += SSP.length()) {
            ShortVector.fromArray(SSP, ssrc1, i)
                       .slice(4, ShortVector.fromArray(SSP, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_I}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexInt() {
        for (int i = 0; i < ISP.loopBound(SIZE); i += ISP.length()) {
            IntVector.fromArray(ISP, isrc1, i)
                     .slice(0, IntVector.fromArray(ISP, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_I}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexInt() {
        for (int i = 0; i < ISP.loopBound(SIZE); i += ISP.length()) {
            IntVector.fromArray(ISP, isrc1, i)
                     .slice(ISP.length(), IntVector.fromArray(ISP, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_I, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexInt() {
        for (int i = 0; i < ISP.loopBound(SIZE); i += ISP.length()) {
            IntVector.fromArray(ISP, isrc1, i)
                     .slice(1, IntVector.fromArray(ISP, isrc2, i), imask)
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index16B_reg", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexInt() {
        for (int i = 0; i < ISP.loopBound(SIZE); i += ISP.length()) {
            IntVector.fromArray(ISP, isrc1, i)
                     .slice(4, IntVector.fromArray(ISP, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index_multiple4_reg_evex", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexInt() {
        for (int i = 0; i < ISP.loopBound(SIZE); i += ISP.length()) {
            IntVector.fromArray(ISP, isrc1, i)
                     .slice(4, IntVector.fromArray(ISP, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_L}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexLong() {
        for (int i = 0; i < LSP.loopBound(SIZE); i += LSP.length()) {
            LongVector.fromArray(LSP, lsrc1, i)
                      .slice(0, LongVector.fromArray(LSP, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_L}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexLong() {
        for (int i = 0; i < LSP.loopBound(SIZE); i += LSP.length()) {
            LongVector.fromArray(LSP, lsrc1, i)
                      .slice(LSP.length(), LongVector.fromArray(LSP, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_L, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexLong() {
        for (int i = 0; i < LSP.loopBound(SIZE); i += LSP.length()) {
            LongVector.fromArray(LSP, lsrc1, i)
                      .slice(1, LongVector.fromArray(LSP, lsrc2, i), lmask)
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index16B_reg", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexLong() {
        for (int i = 0; i < LSP.loopBound(SIZE); i += LSP.length()) {
            LongVector.fromArray(LSP, lsrc1, i)
                      .slice(2, LongVector.fromArray(LSP, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index_multiple4_reg_evex", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexLong() {
        for (int i = 0; i < LSP.loopBound(SIZE); i += LSP.length()) {
            LongVector.fromArray(LSP, lsrc1, i)
                      .slice(2, LongVector.fromArray(LSP, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_F}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexFloat() {
        for (int i = 0; i < FSP.loopBound(SIZE); i += FSP.length()) {
            FloatVector.fromArray(FSP, fsrc1, i)
                       .slice(0, FloatVector.fromArray(FSP, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_F}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexFloat() {
        for (int i = 0; i < FSP.loopBound(SIZE); i += FSP.length()) {
            FloatVector.fromArray(FSP, fsrc1, i)
                       .slice(FSP.length(), FloatVector.fromArray(FSP, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_F, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexFloat() {
        for (int i = 0; i < FSP.loopBound(SIZE); i += FSP.length()) {
            FloatVector.fromArray(FSP, fsrc1, i)
                       .slice(1, FloatVector.fromArray(FSP, fsrc2, i), fmask)
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index16B_reg", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexFloat() {
        for (int i = 0; i < FSP.loopBound(SIZE); i += FSP.length()) {
            FloatVector.fromArray(FSP, fsrc1, i)
                       .slice(4, FloatVector.fromArray(FSP, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index_multiple4_reg_evex", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexFloat() {
        for (int i = 0; i < FSP.loopBound(SIZE); i += FSP.length()) {
            FloatVector.fromArray(FSP, fsrc1, i)
                       .slice(4, FloatVector.fromArray(FSP, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_D}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexDouble() {
        for (int i = 0; i < DSP.loopBound(SIZE); i += DSP.length()) {
            DoubleVector.fromArray(DSP, dsrc1, i)
                        .slice(0, DoubleVector.fromArray(DSP, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_D}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexDouble() {
        for (int i = 0; i < DSP.loopBound(SIZE); i += DSP.length()) {
            DoubleVector.fromArray(DSP, dsrc1, i)
                        .slice(DSP.length(), DoubleVector.fromArray(DSP, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_D, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexDouble() {
        for (int i = 0; i < DSP.loopBound(SIZE); i += DSP.length()) {
            DoubleVector.fromArray(DSP, dsrc1, i)
                        .slice(1, DoubleVector.fromArray(DSP, dsrc2, i), dmask)
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index16B_reg", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexDouble() {
        for (int i = 0; i < DSP.loopBound(SIZE); i += DSP.length()) {
            DoubleVector.fromArray(DSP, dsrc1, i)
                        .slice(2, DoubleVector.fromArray(DSP, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(counts = {"vector_slice_const_origin_GT16B_index_multiple4_reg_evex", " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexDouble() {
        for (int i = 0; i < DSP.loopBound(SIZE); i += DSP.length()) {
            DoubleVector.fromArray(DSP, dsrc1, i)
                        .slice(2, DoubleVector.fromArray(DSP, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
