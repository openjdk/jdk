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
 * @modules jdk.incubator.vector
 * @library /test/lib /
 *
 * @run driver compiler.vectorapi.VectorRearrangeTest
 */

package compiler.vectorapi;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

public class VectorRearrangeTest {
    private static final int LENGTH = 1024;
    private static final Generators random = Generators.G;

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

        Generator<Integer> byteGen = random.uniformInts(Byte.MIN_VALUE, Byte.MAX_VALUE);
        Generator<Integer> shortGen = random.uniformInts(Short.MIN_VALUE, Short.MAX_VALUE);
        for (int i = 0; i < LENGTH; i++) {
            bsrc[i] = byteGen.next().byteValue();
            ssrc[i] = shortGen.next().shortValue();
        }
        random.fill(random.ints(), isrc);
        random.fill(random.longs(), lsrc);
        random.fill(random.floats(), fsrc);
        random.fill(random.doubles(), dsrc);

        int[] nums = {2, 4, 8, 16};
        indexes = new int[4][];
        for (int i = 0; i < 4; i++) {
            indexes[i] = new int[nums[i]];
            random.fill(random.uniformInts(0, nums[i] - 1), indexes[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VB, IRNode.VECTOR_SIZE_8, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public void rearrange_byte64() {
        VectorShuffle<Byte> shuffle = VectorShuffle.fromArray(bspec64, indexes[2], 0);
        for (int i = 0; i < LENGTH; i += bspec64.length()) {
            ByteVector.fromArray(bspec64, bsrc, i)
                      .rearrange(shuffle)
                      .intoArray(bdst, i);
        }
    }

    @Check(test = "rearrange_byte64")
    public void rearrange_byte64_verify() {
        for (int i = 0; i < LENGTH; i += bspec64.length()) {
            for (int j = 0; j < bspec64.length(); j++) {
                Asserts.assertEquals(bsrc[indexes[2][j] + i], bdst[i + j]);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VB, IRNode.VECTOR_SIZE_16, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public void rearrange_byte128() {
        VectorShuffle<Byte> shuffle = VectorShuffle.fromArray(bspec128, indexes[3], 0);
        for (int i = 0; i < LENGTH; i += bspec128.length()) {
            ByteVector.fromArray(bspec128, bsrc, i)
                      .rearrange(shuffle)
                      .intoArray(bdst, i);
        }
    }

    @Check(test = "rearrange_byte128")
    public void rearrange_byte128_verify() {
        for (int i = 0; i < LENGTH; i += bspec128.length()) {
            for (int j = 0; j < bspec128.length(); j++) {
                Asserts.assertEquals(bsrc[indexes[3][j] + i], bdst[i + j]);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_4, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public void rearrange_short64() {
        VectorShuffle<Short> shuffle = VectorShuffle.fromArray(sspec64, indexes[1], 0);
        for (int i = 0; i < LENGTH; i += sspec64.length()) {
            ShortVector.fromArray(sspec64, ssrc, i)
                       .rearrange(shuffle)
                       .intoArray(sdst, i);
        }
    }

    @Check(test = "rearrange_short64")
    public void rearrange_short64_verify() {
        for (int i = 0; i < LENGTH; i += sspec64.length()) {
            for (int j = 0; j < sspec64.length(); j++) {
                Asserts.assertEquals(ssrc[indexes[1][j] + i], sdst[i + j]);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_8, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public void rearrange_short128() {
        VectorShuffle<Short> shuffle = VectorShuffle.fromArray(sspec128, indexes[2], 0);
        for (int i = 0; i < LENGTH; i += sspec128.length()) {
            ShortVector.fromArray(sspec128, ssrc, i)
                       .rearrange(shuffle)
                       .intoArray(sdst, i);
        }
    }

    @Check(test = "rearrange_short128")
    public void rearrange_short128_verify() {
        for (int i = 0; i < LENGTH; i += sspec128.length()) {
            for (int j = 0; j < sspec128.length(); j++) {
                Asserts.assertEquals(ssrc[indexes[2][j] + i], sdst[i + j]);
            }
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

    @Check(test = "rearrange_int64")
    public void rearrange_int64_verify() {
        for (int i = 0; i < LENGTH; i += ispec64.length()) {
            for (int j = 0; j < ispec64.length(); j++) {
                Asserts.assertEquals(isrc[indexes[0][j] + i], idst[i + j]);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VI, IRNode.VECTOR_SIZE_4, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public void rearrange_int128() {
        VectorShuffle<Integer> shuffle = VectorShuffle.fromArray(ispec128, indexes[1], 0);
        for (int i = 0; i < LENGTH; i += ispec128.length()) {
            IntVector.fromArray(ispec128, isrc, i)
                     .rearrange(shuffle)
                     .intoArray(idst, i);
        }
    }

    @Check(test = "rearrange_int128")
    public void rearrange_int128_verify() {
        for (int i = 0; i < LENGTH; i += ispec128.length()) {
            for (int j = 0; j < ispec128.length(); j++) {
                Asserts.assertEquals(isrc[indexes[1][j] + i], idst[i + j]);
            }
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

    @Check(test = "rearrange_long128")
    public void rearrange_long128_verify() {
        for (int i = 0; i < LENGTH; i += lspec128.length()) {
            for (int j = 0; j < lspec128.length(); j++) {
                Asserts.assertEquals(lsrc[indexes[0][j] + i], ldst[i + j]);
            }
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

    @Check(test = "rearrange_float64")
    public void rearrange_float64_verify() {
        for (int i = 0; i < LENGTH; i += fspec64.length()) {
            for (int j = 0; j < fspec64.length(); j++) {
                Asserts.assertEquals(fsrc[indexes[0][j] + i], fdst[i + j]);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VF, IRNode.VECTOR_SIZE_4, " >0 "}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public void rearrange_float128() {
        VectorShuffle<Float> shuffle = VectorShuffle.fromArray(fspec128, indexes[1], 0);
        for (int i = 0; i < LENGTH; i += fspec128.length()) {
            FloatVector.fromArray(fspec128, fsrc, i)
                       .rearrange(shuffle)
                       .intoArray(fdst, i);
        }
    }

    @Check(test = "rearrange_float128")
    public void rearrange_float128_verify() {
        for (int i = 0; i < LENGTH; i += fspec128.length()) {
            for (int j = 0; j < fspec128.length(); j++) {
                Asserts.assertEquals(fsrc[indexes[1][j] + i], fdst[i + j]);
            }
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

    @Check(test = "rearrange_double128")
    public void rearrange_double128_verify() {
        for (int i = 0; i < LENGTH; i += dspec128.length()) {
            for (int j = 0; j < dspec128.length(); j++) {
                Asserts.assertEquals(dsrc[indexes[0][j] + i], ddst[i + j]);
            }
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
