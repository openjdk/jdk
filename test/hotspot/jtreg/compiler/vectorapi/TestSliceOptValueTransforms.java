/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import static compiler.lib.generators.Generators.G;

public class TestSliceOptValueTransforms {
    public static final int SIZE = 1024;

    public static final VectorSpecies<Byte> BSP = ByteVector.SPECIES_PREFERRED;
    public static final VectorSpecies<Short> SSP = ShortVector.SPECIES_PREFERRED;
    public static final VectorSpecies<Integer> ISP = IntVector.SPECIES_PREFERRED;
    public static final VectorSpecies<Long> LSP = LongVector.SPECIES_PREFERRED;

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

        G.fill(G.ints(), isrc1);
        G.fill(G.ints(), isrc2);
        G.fill(G.longs(), lsrc1);
        G.fill(G.longs(), lsrc2);

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
    @IR(counts = {IRNode.VECTOR_SLICE_B, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexByte() {
        for (int i = 0; i < BSP.loopBound(SIZE); i += BSP.length()) {
            ByteVector.fromArray(BSP, bsrc1, i)
                      .slice(1, ByteVector.fromArray(BSP, bsrc2, i))
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
    @IR(counts = {IRNode.VECTOR_SLICE_S, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexShort() {
        for (int i = 0; i < SSP.loopBound(SIZE); i += SSP.length()) {
            ShortVector.fromArray(SSP, ssrc1, i)
                       .slice(1, ShortVector.fromArray(SSP, ssrc2, i))
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
    @IR(counts = {IRNode.VECTOR_SLICE_I, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexInt() {
        for (int i = 0; i < ISP.loopBound(SIZE); i += ISP.length()) {
            IntVector.fromArray(ISP, isrc1, i)
                     .slice(1, IntVector.fromArray(ISP, isrc2, i))
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
    @IR(counts = {IRNode.VECTOR_SLICE_L, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexLong() {
        for (int i = 0; i < LSP.loopBound(SIZE); i += LSP.length()) {
            LongVector.fromArray(LSP, lsrc1, i)
                      .slice(1, LongVector.fromArray(LSP, lsrc2, i))
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

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
