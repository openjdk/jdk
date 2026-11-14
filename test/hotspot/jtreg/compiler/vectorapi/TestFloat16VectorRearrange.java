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
 * @bug 8389741
 * @summary Intrinsify Float16Vector.rearrange on the 16-bit permute path
 * @modules jdk.incubator.vector
 * @library /test/lib /
 *
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import compiler.lib.generators.Generator;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.Float16;
import jdk.incubator.vector.Float16Vector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;

import java.util.Random;

import static compiler.lib.generators.Generators.G;

public class TestFloat16VectorRearrange {
    private static final int LENGTH = 1024;

    private static final VectorSpecies<Float16> S64  = Float16Vector.SPECIES_64;
    private static final VectorSpecies<Float16> S128 = Float16Vector.SPECIES_128;
    private static final VectorSpecies<Float16> S256 = Float16Vector.SPECIES_256;
    private static final VectorSpecies<Float16> S512 = Float16Vector.SPECIES_512;

    private static final short[] SRC = new short[LENGTH];

    private static final int[] ID64  = identity(S64.length());
    private static final int[] IRR64 = irregular(S64.length());
    private static final boolean[] ALT64 = alternating(S64.length());

    private static final int[] ID128  = identity(S128.length());
    private static final int[] IRR128 = irregular(S128.length());
    private static final boolean[] ALT128 = alternating(S128.length());

    private static final int[] ID256  = identity(S256.length());
    private static final int[] IRR256 = irregular(S256.length());
    private static final boolean[] ALT256 = alternating(S256.length());

    private static final int[] ID512  = identity(S512.length());
    private static final int[] IRR512 = irregular(S512.length());
    private static final boolean[] ALT512 = alternating(S512.length());

    private static final short[] DST64_ID  = new short[LENGTH];
    private static final short[] DST64_IRR = new short[LENGTH];
    private static final short[] DST64_MASKED_ID  = new short[LENGTH];
    private static final short[] DST64_MASKED_IRR = new short[LENGTH];

    private static final short[] DST128_ID  = new short[LENGTH];
    private static final short[] DST128_IRR = new short[LENGTH];
    private static final short[] DST128_MASKED_ID  = new short[LENGTH];
    private static final short[] DST128_MASKED_IRR = new short[LENGTH];

    private static final short[] DST256_ID  = new short[LENGTH];
    private static final short[] DST256_IRR = new short[LENGTH];
    private static final short[] DST256_MASKED_ID  = new short[LENGTH];
    private static final short[] DST256_MASKED_IRR = new short[LENGTH];

    private static final short[] DST512_ID  = new short[LENGTH];
    private static final short[] DST512_IRR = new short[LENGTH];
    private static final short[] DST512_MASKED_ID  = new short[LENGTH];
    private static final short[] DST512_MASKED_IRR = new short[LENGTH];

    static {
        Generator<Short> gen = G.float16s();
        for (int i = 0; i < LENGTH; i++) {
            SRC[i] = gen.next();
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_4, " >0 "},
        applyIfCPUFeatureAnd = {"avx512bw", "true", "avx512vl", "true"})
    public void rearrange64() {
        VectorShuffle<Float16> id = VectorShuffle.fromArray(S64, ID64, 0);
        VectorShuffle<Float16> irr = VectorShuffle.fromArray(S64, IRR64, 0);
        for (int i = 0; i < LENGTH; i += S64.length()) {
            Float16Vector.fromArray(S64, SRC, i).rearrange(id).intoArray(DST64_ID, i);
            Float16Vector.fromArray(S64, SRC, i).rearrange(irr).intoArray(DST64_IRR, i);
        }
    }

    @Check(test = "rearrange64")
    public void checkRearrange64() {
        verify_unmasked(S64, ID64, DST64_ID);
        verify_unmasked(S64, IRR64, DST64_IRR);
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_4, " >0 "},
        applyIfCPUFeatureAnd = {"avx512bw", "true", "avx512vl", "true"})
    public void rearrange64Masked() {
        VectorShuffle<Float16> id = VectorShuffle.fromArray(S64, ID64, 0);
        VectorShuffle<Float16> irr = VectorShuffle.fromArray(S64, IRR64, 0);
        VectorMask<Float16> mask = VectorMask.fromArray(S64, ALT64, 0);
        for (int i = 0; i < LENGTH; i += S64.length()) {
            Float16Vector.fromArray(S64, SRC, i).rearrange(id, mask).intoArray(DST64_MASKED_ID, i);
            Float16Vector.fromArray(S64, SRC, i).rearrange(irr, mask).intoArray(DST64_MASKED_IRR, i);
        }
    }

    @Check(test = "rearrange64Masked")
    public void checkRearrange64Masked() {
        verify_masked(S64, ID64, ALT64, DST64_MASKED_ID);
        verify_masked(S64, IRR64, ALT64, DST64_MASKED_IRR);
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_8, " >0 "},
        applyIfCPUFeatureAnd = {"avx512bw", "true", "avx512vl", "true"})
    public void rearrange128() {
        VectorShuffle<Float16> id = VectorShuffle.fromArray(S128, ID128, 0);
        VectorShuffle<Float16> irr = VectorShuffle.fromArray(S128, IRR128, 0);
        for (int i = 0; i < LENGTH; i += S128.length()) {
            Float16Vector.fromArray(S128, SRC, i).rearrange(id).intoArray(DST128_ID, i);
            Float16Vector.fromArray(S128, SRC, i).rearrange(irr).intoArray(DST128_IRR, i);
        }
    }

    @Check(test = "rearrange128")
    public void checkRearrange128() {
        verify_unmasked(S128, ID128, DST128_ID);
        verify_unmasked(S128, IRR128, DST128_IRR);
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_8, " >0 "},
        applyIfCPUFeatureAnd = {"avx512bw", "true", "avx512vl", "true"})
    public void rearrange128Masked() {
        VectorShuffle<Float16> id = VectorShuffle.fromArray(S128, ID128, 0);
        VectorShuffle<Float16> irr = VectorShuffle.fromArray(S128, IRR128, 0);
        VectorMask<Float16> mask = VectorMask.fromArray(S128, ALT128, 0);
        for (int i = 0; i < LENGTH; i += S128.length()) {
            Float16Vector.fromArray(S128, SRC, i).rearrange(id, mask).intoArray(DST128_MASKED_ID, i);
            Float16Vector.fromArray(S128, SRC, i).rearrange(irr, mask).intoArray(DST128_MASKED_IRR, i);
        }
    }

    @Check(test = "rearrange128Masked")
    public void checkRearrange128Masked() {
        verify_masked(S128, ID128, ALT128, DST128_MASKED_ID);
        verify_masked(S128, IRR128, ALT128, DST128_MASKED_IRR);
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_16, " >0 "},
        applyIfCPUFeatureAnd = {"avx512bw", "true", "avx512vl", "true"})
    public void rearrange256() {
        VectorShuffle<Float16> id = VectorShuffle.fromArray(S256, ID256, 0);
        VectorShuffle<Float16> irr = VectorShuffle.fromArray(S256, IRR256, 0);
        for (int i = 0; i < LENGTH; i += S256.length()) {
            Float16Vector.fromArray(S256, SRC, i).rearrange(id).intoArray(DST256_ID, i);
            Float16Vector.fromArray(S256, SRC, i).rearrange(irr).intoArray(DST256_IRR, i);
        }
    }

    @Check(test = "rearrange256")
    public void checkRearrange256() {
        verify_unmasked(S256, ID256, DST256_ID);
        verify_unmasked(S256, IRR256, DST256_IRR);
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_16, " >0 "},
        applyIfCPUFeatureAnd = {"avx512bw", "true", "avx512vl", "true"})
    public void rearrange256Masked() {
        VectorShuffle<Float16> id = VectorShuffle.fromArray(S256, ID256, 0);
        VectorShuffle<Float16> irr = VectorShuffle.fromArray(S256, IRR256, 0);
        VectorMask<Float16> mask = VectorMask.fromArray(S256, ALT256, 0);
        for (int i = 0; i < LENGTH; i += S256.length()) {
            Float16Vector.fromArray(S256, SRC, i).rearrange(id, mask).intoArray(DST256_MASKED_ID, i);
            Float16Vector.fromArray(S256, SRC, i).rearrange(irr, mask).intoArray(DST256_MASKED_IRR, i);
        }
    }

    @Check(test = "rearrange256Masked")
    public void checkRearrange256Masked() {
        verify_masked(S256, ID256, ALT256, DST256_MASKED_ID);
        verify_masked(S256, IRR256, ALT256, DST256_MASKED_IRR);
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_32, " >0 "},
        applyIfCPUFeature = {"avx512bw", "true"})
    public void rearrange512() {
        VectorShuffle<Float16> id = VectorShuffle.fromArray(S512, ID512, 0);
        VectorShuffle<Float16> irr = VectorShuffle.fromArray(S512, IRR512, 0);
        for (int i = 0; i < LENGTH; i += S512.length()) {
            Float16Vector.fromArray(S512, SRC, i).rearrange(id).intoArray(DST512_ID, i);
            Float16Vector.fromArray(S512, SRC, i).rearrange(irr).intoArray(DST512_IRR, i);
        }
    }

    @Check(test = "rearrange512")
    public void checkRearrange512() {
        verify_unmasked(S512, ID512, DST512_ID);
        verify_unmasked(S512, IRR512, DST512_IRR);
    }

    @Test
    @IR(counts = {IRNode.REARRANGE_VS, IRNode.VECTOR_SIZE_32, " >0 "},
        applyIfCPUFeature = {"avx512bw", "true"})
    public void rearrange512Masked() {
        VectorShuffle<Float16> id = VectorShuffle.fromArray(S512, ID512, 0);
        VectorShuffle<Float16> irr = VectorShuffle.fromArray(S512, IRR512, 0);
        VectorMask<Float16> mask = VectorMask.fromArray(S512, ALT512, 0);
        for (int i = 0; i < LENGTH; i += S512.length()) {
            Float16Vector.fromArray(S512, SRC, i).rearrange(id, mask).intoArray(DST512_MASKED_ID, i);
            Float16Vector.fromArray(S512, SRC, i).rearrange(irr, mask).intoArray(DST512_MASKED_IRR, i);
        }
    }

    @Check(test = "rearrange512Masked")
    public void checkRearrange512Masked() {
        verify_masked(S512, ID512, ALT512, DST512_MASKED_ID);
        verify_masked(S512, IRR512, ALT512, DST512_MASKED_IRR);
    }

    private static void verify_unmasked(VectorSpecies<Float16> species, int[] indexes, short[] dst) {
        int n = species.length();
        VectorShuffle<Float16> shuffle = VectorShuffle.fromArray(species, indexes, 0);
        for (int i = 0; i < LENGTH; i += n) {
            for (int j = 0; j < n; j++) {
                int ei = Integer.remainderUnsigned(shuffle.laneSource(j), n);
                Verify.checkEQWithRawBits(Float16.shortBitsToFloat16(dst[i + j]),
                                          Float16.shortBitsToFloat16(SRC[i + ei]));
            }
        }
    }

    private static void verify_masked(VectorSpecies<Float16> species, int[] indexes,
                                      boolean[] maskBits, short[] dst) {
        int n = species.length();
        VectorShuffle<Float16> shuffle = VectorShuffle.fromArray(species, indexes, 0);
        VectorMask<Float16> mask = VectorMask.fromArray(species, maskBits, 0);
        for (int i = 0; i < LENGTH; i += n) {
            for (int j = 0; j < n; j++) {
                short expected = 0;
                if (mask.laneIsSet(j)) {
                    int ei = Integer.remainderUnsigned(shuffle.laneSource(j), n);
                    expected = SRC[i + ei];
                }
                Verify.checkEQWithRawBits(Float16.shortBitsToFloat16(dst[i + j]),
                                          Float16.shortBitsToFloat16(expected));
            }
        }
    }

    private static int[] identity(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        return a;
    }

    private static int[] irregular(int n) {
        Random r = new Random(n);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.nextInt(n);
        }
        return a;
    }

    private static boolean[] alternating(int n) {
        boolean[] a = new boolean[n];
        for (int i = 0; i < n; i++) {
            a[i] = (i & 1) == 0;
        }
        return a;
    }
}
