/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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
 * @bug 8350463
 * @summary AArch64: Add vector rearrange support for small lane count vectors
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx.*") | os.arch=="aarch64"
 * @modules jdk.incubator.vector
 * @library /test/lib /
 *
 * @run driver compiler.vectorapi.VectorRearrangeTest
 */

package compiler.vectorapi;

import jdk.incubator.vector.*;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

public class VectorRearrangeTest {
    private static final int LENGTH = 2048;
    private static final Random random = Utils.getRandomInstance();

    private static final VectorSpecies<Byte> bspec128    = ByteVector.SPECIES_128;
    private static final VectorSpecies<Short> sspec128   = ShortVector.SPECIES_128;
    private static final VectorSpecies<Integer> ispec128 = IntVector.SPECIES_128;
    private static final VectorSpecies<Long> lspec128    = LongVector.SPECIES_128;
    private static final VectorSpecies<Float> fspec128   = FloatVector.SPECIES_128;
    private static final VectorSpecies<Double> dspec128  = DoubleVector.SPECIES_128;
    private static final VectorSpecies<Byte> bspec64     = ByteVector.SPECIES_64;
    private static final VectorSpecies<Short> sspec64    = ShortVector.SPECIES_64;
    private static final VectorSpecies<Integer> ispec64  = IntVector.SPECIES_64;
    private static final VectorSpecies<Float> fspec64    = FloatVector.SPECIES_64;

    private static byte[]   bsrc;
    private static short[]  ssrc;
    private static int[]    isrc;
    private static long[]   lsrc;
    private static float[]  fsrc;
    private static double[] dsrc;

    private static byte[]   bdst;
    private static short[]  sdst;
    private static int[]    idst;
    private static long[]   ldst;
    private static float[]  fdst;
    private static double[] ddst;

    private static int[][] indexes;

    static {
        bsrc = new byte[LENGTH];
        ssrc = new short[LENGTH];
        isrc = new int[LENGTH];
        lsrc = new long[LENGTH];
        fsrc = new float[LENGTH];
        dsrc = new double[LENGTH];
        bdst = new byte[LENGTH];
        sdst = new short[LENGTH];
        idst = new int[LENGTH];
        ldst = new long[LENGTH];
        fdst = new float[LENGTH];
        ddst = new double[LENGTH];

        for (int i = 0; i < LENGTH; ++i) {
            bsrc[i] = (byte)random.nextInt();
            ssrc[i] = (short)random.nextInt();
            isrc[i] = random.nextInt();
            lsrc[i] = random.nextLong();
            fsrc[i] = random.nextFloat();
            dsrc[i] = random.nextDouble();
        }

        int[] nums = {2, 4, 8, 16};
        indexes = new int[4][];
        for (int i = 0; i < 4; i++) {
            indexes[i] = new int[nums[i]];
            for (int j = 0; j < nums[i]; j++) {
                indexes[i][j] = random.nextInt() & (nums[i] - 1);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VB, IRNode.VECTOR_SIZE_8, " >0 "})
    public void rearrange_byte64() {
        VectorShuffle<Byte> shuffle = VectorShuffle.fromArray(bspec64, indexes[2], 0);
        for (int i = 0; i < LENGTH; i += bspec64.length()) {
            ByteVector.fromArray(bspec64, bsrc, i)
                      .rearrange(shuffle)
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VB, IRNode.VECTOR_SIZE_16, " >0 "})
    public void rearrange_byte128() {
        VectorShuffle<Byte> shuffle = VectorShuffle.fromArray(bspec128, indexes[3], 0);
        for (int i = 0; i < LENGTH; i += bspec128.length()) {
            ByteVector.fromArray(bspec128, bsrc, i)
                      .rearrange(shuffle)
                      .intoArray(bdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_4, " >0 "})
    public void rearrange_short64() {
        VectorShuffle<Short> shuffle = VectorShuffle.fromArray(sspec64, indexes[1], 0);
        for (int i = 0; i < LENGTH; i += sspec64.length()) {
            ShortVector.fromArray(sspec64, ssrc, i)
                       .rearrange(shuffle)
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_8, " >0 "})
    public void rearrange_short128() {
        VectorShuffle<Short> shuffle = VectorShuffle.fromArray(sspec128, indexes[2], 0);
        for (int i = 0; i < LENGTH; i += sspec128.length()) {
            ShortVector.fromArray(sspec128, ssrc, i)
                       .rearrange(shuffle)
                       .intoArray(sdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VI, IRNode.VECTOR_SIZE_2, " >0 "}, applyIfCPUFeature = {"asimd", "true"})
    public void rearrange_int64() {
        VectorShuffle<Integer> shuffle = VectorShuffle.fromArray(ispec64, indexes[0], 0);
        for (int i = 0; i < LENGTH; i += ispec64.length()) {
            IntVector.fromArray(ispec64, isrc, i)
                     .rearrange(shuffle)
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VI, IRNode.VECTOR_SIZE_4, " >0 "})
    public void rearrange_int128() {
        VectorShuffle<Integer> shuffle = VectorShuffle.fromArray(ispec128, indexes[1], 0);
        for (int i = 0; i < LENGTH; i += ispec128.length()) {
            IntVector.fromArray(ispec128, isrc, i)
                     .rearrange(shuffle)
                     .intoArray(idst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VL, IRNode.VECTOR_SIZE_2, " >0 "}, applyIfCPUFeature = {"asimd", "true"})
    public void rearrange_long128() {
        VectorShuffle<Long> shuffle = VectorShuffle.fromArray(lspec128, indexes[0], 0);
        for (int i = 0; i < LENGTH; i += lspec128.length()) {
            LongVector.fromArray(lspec128, lsrc, i)
                      .rearrange(shuffle)
                      .intoArray(ldst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VF, IRNode.VECTOR_SIZE_2, " >0 "}, applyIfCPUFeature = {"asimd", "true"})
    public void rearrange_float64() {
        VectorShuffle<Float> shuffle = VectorShuffle.fromArray(fspec64, indexes[0], 0);
        for (int i = 0; i < LENGTH; i += fspec64.length()) {
            FloatVector.fromArray(fspec64, fsrc, i)
                       .rearrange(shuffle)
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VF, IRNode.VECTOR_SIZE_4, " >0 "})
    public void rearrange_float128() {
        VectorShuffle<Float> shuffle = VectorShuffle.fromArray(fspec128, indexes[1], 0);
        for (int i = 0; i < LENGTH; i += fspec128.length()) {
            FloatVector.fromArray(fspec128, fsrc, i)
                       .rearrange(shuffle)
                       .intoArray(fdst, i);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VD, IRNode.VECTOR_SIZE_2, " >0 "}, applyIfCPUFeature = {"asimd", "true"})
    public void rearrange_double128() {
        VectorShuffle<Double> shuffle = VectorShuffle.fromArray(dspec128, indexes[0], 0);
        for (int i = 0; i < LENGTH; i += dspec128.length()) {
            DoubleVector.fromArray(dspec128, dsrc, i)
                        .rearrange(shuffle)
                        .intoArray(ddst, i);
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector", "-XX:-TieredCompilation")
                     .start();
    }
}
