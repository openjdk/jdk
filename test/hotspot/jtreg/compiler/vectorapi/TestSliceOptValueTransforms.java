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

    public static final VectorSpecies<Byte> B256 = ByteVector.SPECIES_256;
    public static final VectorSpecies<Byte> B512 = ByteVector.SPECIES_512;
    public static final VectorSpecies<Short> S256 = ShortVector.SPECIES_256;
    public static final VectorSpecies<Short> S512 = ShortVector.SPECIES_512;
    public static final VectorSpecies<Integer> I256 = IntVector.SPECIES_256;
    public static final VectorSpecies<Integer> I512 = IntVector.SPECIES_512;
    public static final VectorSpecies<Long> L256 = LongVector.SPECIES_256;
    public static final VectorSpecies<Long> L512 = LongVector.SPECIES_512;
    public static final VectorSpecies<Float> F256 = FloatVector.SPECIES_256;
    public static final VectorSpecies<Float> F512 = FloatVector.SPECIES_512;
    public static final VectorSpecies<Double> D256 = DoubleVector.SPECIES_256;
    public static final VectorSpecies<Double> D512 = DoubleVector.SPECIES_512;

    public static final VectorMask<Byte> bmask = VectorMask.fromLong(B256, 0xF);
    public static final VectorMask<Short> smask = VectorMask.fromLong(S256, 0xF);
    public static final VectorMask<Integer> imask = VectorMask.fromLong(I256, 0xF);
    public static final VectorMask<Long> lmask = VectorMask.fromLong(L256, 0xF);
    public static final VectorMask<Float> fmask = VectorMask.fromLong(F256, 0xF);
    public static final VectorMask<Double> dmask = VectorMask.fromLong(D256, 0x3);

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

    // Byte tests

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_B}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexByte() {
        for (int i = 0; i < B256.loopBound(SIZE); i += B256.length()) {
            ByteVector.fromArray(B256, bsrc1, i)
                      .slice(0, ByteVector.fromArray(B256, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_B}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexByte() {
        for (int i = 0; i < B256.loopBound(SIZE); i += B256.length()) {
            ByteVector.fromArray(B256, bsrc1, i)
                      .slice(B256.length(), ByteVector.fromArray(B256, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_B, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexByte() {
        for (int i = 0; i < B256.loopBound(SIZE); i += B256.length()) {
            ByteVector.fromArray(B256, bsrc1, i)
                      .slice(1, ByteVector.fromArray(B256, bsrc2, i), bmask)
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_16_16B_AVX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexByte() {
        for (int i = 0; i < B256.loopBound(SIZE); i += B256.length()) {
            ByteVector.fromArray(B256, bsrc1, i)
                      .slice(16, ByteVector.fromArray(B256, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexByte() {
        for (int i = 0; i < B256.loopBound(SIZE); i += B256.length()) {
            ByteVector.fromArray(B256, bsrc1, i)
                      .slice(8, ByteVector.fromArray(B256, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_GT16_AND_LT48_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT16AndLT48SliceIndexByte() {
        for (int i = 0; i < B256.loopBound(SIZE); i += B256.length()) {
            ByteVector.fromArray(B256, bsrc1, i)
                      .slice(17, ByteVector.fromArray(B256, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_LT16_OR_GT48_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testLT16SliceIndexByte() {
        for (int i = 0; i < B256.loopBound(SIZE); i += B256.length()) {
            ByteVector.fromArray(B256, bsrc1, i)
                      .slice(3, ByteVector.fromArray(B256, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_LT16_OR_GT48_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT48SliceIndexByte() {
        for (int i = 0; i < B512.loopBound(SIZE); i += B512.length()) {
            ByteVector.fromArray(B512, bsrc1, i)
                      .slice(49, ByteVector.fromArray(B512, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    // Short tests

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_S}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexShort() {
        for (int i = 0; i < S256.loopBound(SIZE); i += S256.length()) {
            ShortVector.fromArray(S256, ssrc1, i)
                       .slice(0, ShortVector.fromArray(S256, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_S}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexShort() {
        for (int i = 0; i < S256.loopBound(SIZE); i += S256.length()) {
            ShortVector.fromArray(S256, ssrc1, i)
                       .slice(S256.length(), ShortVector.fromArray(S256, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_S, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexShort() {
        for (int i = 0; i < S256.loopBound(SIZE); i += S256.length()) {
            ShortVector.fromArray(S256, ssrc1, i)
                       .slice(1, ShortVector.fromArray(S256, ssrc2, i), smask)
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_16_16B_AVX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexShort() {
        for (int i = 0; i < S256.loopBound(SIZE); i += S256.length()) {
            ShortVector.fromArray(S256, ssrc1, i)
                       .slice(8, ShortVector.fromArray(S256, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexShort() {
        for (int i = 0; i < S256.loopBound(SIZE); i += S256.length()) {
            ShortVector.fromArray(S256, ssrc1, i)
                       .slice(4, ShortVector.fromArray(S256, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_GT16_AND_LT48_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT16AndLT48SliceIndexShort() {
        for (int i = 0; i < S256.loopBound(SIZE); i += S256.length()) {
            ShortVector.fromArray(S256, ssrc1, i)
                       .slice(9, ShortVector.fromArray(S256, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_LT16_OR_GT48_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testLT16SliceIndexShort() {
        for (int i = 0; i < S256.loopBound(SIZE); i += S256.length()) {
            ShortVector.fromArray(S256, ssrc1, i)
                       .slice(3, ShortVector.fromArray(S256, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_LT16_OR_GT48_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT48SliceIndexShort() {
        for (int i = 0; i < S512.loopBound(SIZE); i += S512.length()) {
            ShortVector.fromArray(S512, ssrc1, i)
                       .slice(25, ShortVector.fromArray(S512, ssrc2, i))
                       .intoArray(sdst, i);
        }
    }

    // Int tests

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_I}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexInt() {
        for (int i = 0; i < I256.loopBound(SIZE); i += I256.length()) {
            IntVector.fromArray(I256, isrc1, i)
                     .slice(0, IntVector.fromArray(I256, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_I}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexInt() {
        for (int i = 0; i < I256.loopBound(SIZE); i += I256.length()) {
            IntVector.fromArray(I256, isrc1, i)
                     .slice(I256.length(), IntVector.fromArray(I256, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_I, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexInt() {
        for (int i = 0; i < I256.loopBound(SIZE); i += I256.length()) {
            IntVector.fromArray(I256, isrc1, i)
                     .slice(1, IntVector.fromArray(I256, isrc2, i), imask)
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_16_16B_AVX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexInt() {
        for (int i = 0; i < I256.loopBound(SIZE); i += I256.length()) {
            IntVector.fromArray(I256, isrc1, i)
                     .slice(4, IntVector.fromArray(I256, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexInt() {
        for (int i = 0; i < I256.loopBound(SIZE); i += I256.length()) {
            IntVector.fromArray(I256, isrc1, i)
                     .slice(4, IntVector.fromArray(I256, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testLT16SliceIndexInt() {
        for (int i = 0; i < I256.loopBound(SIZE); i += I256.length()) {
            IntVector.fromArray(I256, isrc1, i)
                     .slice(2, IntVector.fromArray(I256, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT16AndLT48SliceIndexInt() {
        for (int i = 0; i < I256.loopBound(SIZE); i += I256.length()) {
            IntVector.fromArray(I256, isrc1, i)
                     .slice(5, IntVector.fromArray(I256, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT48SliceIndexInt() {
        for (int i = 0; i < I512.loopBound(SIZE); i += I512.length()) {
            IntVector.fromArray(I512, isrc1, i)
                     .slice(13, IntVector.fromArray(I512, isrc2, i))
                     .intoArray(idst, i);
        }
    }

    // Long tests

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_L}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexLong() {
        for (int i = 0; i < L256.loopBound(SIZE); i += L256.length()) {
            LongVector.fromArray(L256, lsrc1, i)
                      .slice(0, LongVector.fromArray(L256, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_L}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexLong() {
        for (int i = 0; i < L256.loopBound(SIZE); i += L256.length()) {
            LongVector.fromArray(L256, lsrc1, i)
                      .slice(L256.length(), LongVector.fromArray(L256, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_L, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexLong() {
        for (int i = 0; i < L256.loopBound(SIZE); i += L256.length()) {
            LongVector.fromArray(L256, lsrc1, i)
                      .slice(1, LongVector.fromArray(L256, lsrc2, i), lmask)
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_16_16B_AVX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexLong() {
        for (int i = 0; i < L256.loopBound(SIZE); i += L256.length()) {
            LongVector.fromArray(L256, lsrc1, i)
                      .slice(2, LongVector.fromArray(L256, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexLong() {
        for (int i = 0; i < L256.loopBound(SIZE); i += L256.length()) {
            LongVector.fromArray(L256, lsrc1, i)
                      .slice(2, LongVector.fromArray(L256, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testLT16SliceIndexLong() {
        for (int i = 0; i < L256.loopBound(SIZE); i += L256.length()) {
            LongVector.fromArray(L256, lsrc1, i)
                      .slice(1, LongVector.fromArray(L256, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT16AndLT48SliceIndexLong() {
        for (int i = 0; i < L256.loopBound(SIZE); i += L256.length()) {
            LongVector.fromArray(L256, lsrc1, i)
                      .slice(3, LongVector.fromArray(L256, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT48SliceIndexLong() {
        for (int i = 0; i < L512.loopBound(SIZE); i += L512.length()) {
            LongVector.fromArray(L512, lsrc1, i)
                      .slice(7, LongVector.fromArray(L512, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    // Float tests

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_F}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexFloat() {
        for (int i = 0; i < F256.loopBound(SIZE); i += F256.length()) {
            FloatVector.fromArray(F256, fsrc1, i)
                       .slice(0, FloatVector.fromArray(F256, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_F}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexFloat() {
        for (int i = 0; i < F256.loopBound(SIZE); i += F256.length()) {
            FloatVector.fromArray(F256, fsrc1, i)
                       .slice(F256.length(), FloatVector.fromArray(F256, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_F, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexFloat() {
        for (int i = 0; i < F256.loopBound(SIZE); i += F256.length()) {
            FloatVector.fromArray(F256, fsrc1, i)
                       .slice(1, FloatVector.fromArray(F256, fsrc2, i), fmask)
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_16_16B_AVX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexFloat() {
        for (int i = 0; i < F256.loopBound(SIZE); i += F256.length()) {
            FloatVector.fromArray(F256, fsrc1, i)
                       .slice(4, FloatVector.fromArray(F256, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexFloat() {
        for (int i = 0; i < F256.loopBound(SIZE); i += F256.length()) {
            FloatVector.fromArray(F256, fsrc1, i)
                       .slice(4, FloatVector.fromArray(F256, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testLT16SliceIndexFloat() {
        for (int i = 0; i < F256.loopBound(SIZE); i += F256.length()) {
            FloatVector.fromArray(F256, fsrc1, i)
                       .slice(2, FloatVector.fromArray(F256, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT16AndLT48SliceIndexFloat() {
        for (int i = 0; i < F256.loopBound(SIZE); i += F256.length()) {
            FloatVector.fromArray(F256, fsrc1, i)
                       .slice(5, FloatVector.fromArray(F256, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT48SliceIndexFloat() {
        for (int i = 0; i < F512.loopBound(SIZE); i += F512.length()) {
            FloatVector.fromArray(F512, fsrc1, i)
                       .slice(13, FloatVector.fromArray(F512, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    // Double tests

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_D}, applyIfCPUFeatureAnd = {"avx2", "true"})
    public void testZeroSliceIndexDouble() {
        for (int i = 0; i < D256.loopBound(SIZE); i += D256.length()) {
            DoubleVector.fromArray(D256, dsrc1, i)
                        .slice(0, DoubleVector.fromArray(D256, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_SLICE_D}, applyIfCPUFeature = {"avx2", "true"})
    public void testMaxSliceIndexDouble() {
        for (int i = 0; i < D256.loopBound(SIZE); i += D256.length()) {
            DoubleVector.fromArray(D256, dsrc1, i)
                        .slice(D256.length(), DoubleVector.fromArray(D256, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE_D, IRNode.VECTOR_SIZE_ANY, " >0 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testConstantSliceIndexDouble() {
        for (int i = 0; i < D256.loopBound(SIZE); i += D256.length()) {
            DoubleVector.fromArray(D256, dsrc1, i)
                        .slice(1, DoubleVector.fromArray(D256, dsrc2, i), dmask)
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_16_16B_AVX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeatureAnd = {"avx512f", "false", "avx2", "true"})
    public void test16BSliceIndexDouble() {
        for (int i = 0; i < D256.loopBound(SIZE); i += D256.length()) {
            DoubleVector.fromArray(D256, dsrc1, i)
                        .slice(2, DoubleVector.fromArray(D256, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testMultipleOf4BSliceIndexDouble() {
        for (int i = 0; i < D256.loopBound(SIZE); i += D256.length()) {
            DoubleVector.fromArray(D256, dsrc1, i)
                        .slice(2, DoubleVector.fromArray(D256, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testLT16SliceIndexDouble() {
        for (int i = 0; i < D256.loopBound(SIZE); i += D256.length()) {
            DoubleVector.fromArray(D256, dsrc1, i)
                        .slice(1, DoubleVector.fromArray(D256, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT16AndLT48SliceIndexDouble() {
        for (int i = 0; i < D256.loopBound(SIZE); i += D256.length()) {
            DoubleVector.fromArray(D256, dsrc1, i)
                        .slice(3, DoubleVector.fromArray(D256, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.X86_VECTOR_SLICE_CONST_ORIGIN_MULTIPLE4_GT16B_EVEX, " >0 "},
        phase = {CompilePhase.MATCHING}, applyIfCPUFeature = {"avx512vl", "true"})
    public void testGT48SliceIndexDouble() {
        for (int i = 0; i < D512.loopBound(SIZE); i += D512.length()) {
            DoubleVector.fromArray(D512, dsrc1, i)
                        .slice(7, DoubleVector.fromArray(D512, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
