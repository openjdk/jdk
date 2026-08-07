/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

package org.openjdk.bench.jdk.incubator.vector;

import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
@Fork(value = 2, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorBlendOpts {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    @Param({"1024"})
    private int SIZE;

    private boolean[] mask_arr;
    private byte[] ba, bb, bc, br;
    private short[] sa, sb, sc, sr;
    private int[] ia, ib, ic, ir;
    private long[] la, lb, lc, lr;
    private float[] fa, fb, fc, fr;
    private double[] da, db, dc, dr;

    @Setup(Level.Trial)
    public void init() {
        Random r = new Random(1024);
        mask_arr = new boolean[SIZE];
        ba = new byte[SIZE];
        bb = new byte[SIZE];
        bc = new byte[SIZE];
        br = new byte[SIZE];
        sa = new short[SIZE];
        sb = new short[SIZE];
        sc = new short[SIZE];
        sr = new short[SIZE];
        ia = new int[SIZE];
        ib = new int[SIZE];
        ic = new int[SIZE];
        ir = new int[SIZE];
        la = new long[SIZE];
        lb = new long[SIZE];
        lc = new long[SIZE];
        lr = new long[SIZE];
        fa = new float[SIZE];
        fb = new float[SIZE];
        fc = new float[SIZE];
        fr = new float[SIZE];
        da = new double[SIZE];
        db = new double[SIZE];
        dc = new double[SIZE];
        dr = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            mask_arr[i] = r.nextBoolean();
            ba[i] = (byte) r.nextInt();
            bb[i] = (byte) r.nextInt();
            bc[i] = (byte) r.nextInt();
            sa[i] = (short) r.nextInt();
            sb[i] = (short) r.nextInt();
            sc[i] = (short) r.nextInt();
            ia[i] = r.nextInt();
            ib[i] = r.nextInt();
            ic[i] = r.nextInt();
            la[i] = r.nextLong();
            lb[i] = r.nextLong();
            lc[i] = r.nextLong();
            fa[i] = r.nextFloat();
            fb[i] = r.nextFloat();
            fc[i] = r.nextFloat();
            da[i] = r.nextDouble();
            db[i] = r.nextDouble();
            dc[i] = r.nextDouble();
        }
    }

    // VectorBlend(a, b, AllOnesMask) => b

    @Benchmark
    public void identityAllOnesByte() {
        VectorMask<Byte> allTrue = B_SPECIES.maskAll(true);
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        for (int i = 0; i < B_SPECIES.loopBound(SIZE); i += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, ba, i);
            av.blend(bv, allTrue).intoArray(br, i);
        }
    }

    @Benchmark
    public void identityAllOnesShort() {
        VectorMask<Short> allTrue = S_SPECIES.maskAll(true);
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        for (int i = 0; i < S_SPECIES.loopBound(SIZE); i += S_SPECIES.length()) {
            ShortVector av = ShortVector.fromArray(S_SPECIES, sa, i);
            av.blend(bv, allTrue).intoArray(sr, i);
        }
    }

    @Benchmark
    public void identityAllOnesInt() {
        VectorMask<Integer> allTrue = I_SPECIES.maskAll(true);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        for (int i = 0; i < I_SPECIES.loopBound(SIZE); i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            av.blend(bv, allTrue).intoArray(ir, i);
        }
    }

    @Benchmark
    public void identityAllOnesLong() {
        VectorMask<Long> allTrue = L_SPECIES.maskAll(true);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        for (int i = 0; i < L_SPECIES.loopBound(SIZE); i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            av.blend(bv, allTrue).intoArray(lr, i);
        }
    }

    @Benchmark
    public void identityAllOnesFloat() {
        VectorMask<Float> allTrue = F_SPECIES.maskAll(true);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        for (int i = 0; i < F_SPECIES.loopBound(SIZE); i += F_SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(F_SPECIES, fa, i);
            av.blend(bv, allTrue).intoArray(fr, i);
        }
    }

    @Benchmark
    public void identityAllOnesDouble() {
        VectorMask<Double> allTrue = D_SPECIES.maskAll(true);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        for (int i = 0; i < D_SPECIES.loopBound(SIZE); i += D_SPECIES.length()) {
            DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, i);
            av.blend(bv, allTrue).intoArray(dr, i);
        }
    }

    // Pattern: VectorBlend(a, b, AllZerosMask) => a

    @Benchmark
    public void identityAllZerosByte() {
        VectorMask<Byte> allFalse = B_SPECIES.maskAll(false);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        for (int i = 0; i < B_SPECIES.loopBound(SIZE); i += B_SPECIES.length()) {
            ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, i);
            av.blend(bv, allFalse).intoArray(br, i);
        }
    }

    @Benchmark
    public void identityAllZerosShort() {
        VectorMask<Short> allFalse = S_SPECIES.maskAll(false);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        for (int i = 0; i < S_SPECIES.loopBound(SIZE); i += S_SPECIES.length()) {
            ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, i);
            av.blend(bv, allFalse).intoArray(sr, i);
        }
    }

    @Benchmark
    public void identityAllZerosInt() {
        VectorMask<Integer> allFalse = I_SPECIES.maskAll(false);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        for (int i = 0; i < I_SPECIES.loopBound(SIZE); i += I_SPECIES.length()) {
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            av.blend(bv, allFalse).intoArray(ir, i);
        }
    }

    @Benchmark
    public void identityAllZerosLong() {
        VectorMask<Long> allFalse = L_SPECIES.maskAll(false);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        for (int i = 0; i < L_SPECIES.loopBound(SIZE); i += L_SPECIES.length()) {
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            av.blend(bv, allFalse).intoArray(lr, i);
        }
    }

    @Benchmark
    public void identityAllZerosFloat() {
        VectorMask<Float> allFalse = F_SPECIES.maskAll(false);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        for (int i = 0; i < F_SPECIES.loopBound(SIZE); i += F_SPECIES.length()) {
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            av.blend(bv, allFalse).intoArray(fr, i);
        }
    }

    @Benchmark
    public void identityAllZerosDouble() {
        VectorMask<Double> allFalse = D_SPECIES.maskAll(false);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        for (int i = 0; i < D_SPECIES.loopBound(SIZE); i += D_SPECIES.length()) {
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            av.blend(bv, allFalse).intoArray(dr, i);
        }
    }

    // Pattern: VectorBlend(VectorBlend(a, b, m), c, m) => VectorBlend(a, c, m)

    @Benchmark
    public void nestedBlendOuterByte() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, mask_arr, 0);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector cv = ByteVector.fromArray(B_SPECIES, bc, 0);
        for (int i = 0; i < B_SPECIES.loopBound(SIZE); i += B_SPECIES.length()) {
            ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, i);
            av.blend(bv, mask).blend(cv, mask).intoArray(br, i);
        }
    }

    @Benchmark
    public void nestedBlendOuterShort() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, mask_arr, 0);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector cv = ShortVector.fromArray(S_SPECIES, sc, 0);
        for (int i = 0; i < S_SPECIES.loopBound(SIZE); i += S_SPECIES.length()) {
            ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, i);
            av.blend(bv, mask).blend(cv, mask).intoArray(sr, i);
        }
    }

    @Benchmark
    public void nestedBlendOuterInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int i = 0; i < I_SPECIES.loopBound(SIZE); i += I_SPECIES.length()) {
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            av.blend(bv, mask).blend(cv, mask).intoArray(ir, i);
        }
    }

    @Benchmark
    public void nestedBlendOuterLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, mask_arr, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        for (int i = 0; i < L_SPECIES.loopBound(SIZE); i += L_SPECIES.length()) {
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            av.blend(bv, mask).blend(cv, mask).intoArray(lr, i);
        }
    }

    @Benchmark
    public void nestedBlendOuterFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, mask_arr, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        for (int i = 0; i < F_SPECIES.loopBound(SIZE); i += F_SPECIES.length()) {
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            av.blend(bv, mask).blend(cv, mask).intoArray(fr, i);
        }
    }

    @Benchmark
    public void nestedBlendOuterDouble() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, mask_arr, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, 0);
        for (int i = 0; i < D_SPECIES.loopBound(SIZE); i += D_SPECIES.length()) {
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            av.blend(bv, mask).blend(cv, mask).intoArray(dr, i);
        }
    }

    // Pattern: VectorBlend(a, VectorBlend(b, c, m), m) => VectorBlend(a, c, m)

    @Benchmark
    public void nestedBlendInnerByte() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, mask_arr, 0);
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector cv = ByteVector.fromArray(B_SPECIES, bc, 0);
        for (int i = 0; i < B_SPECIES.loopBound(SIZE); i += B_SPECIES.length()) {
            ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, i);
            av.blend(bv.blend(cv, mask), mask).intoArray(br, i);
        }
    }

    @Benchmark
    public void nestedBlendInnerShort() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, mask_arr, 0);
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector cv = ShortVector.fromArray(S_SPECIES, sc, 0);
        for (int i = 0; i < S_SPECIES.loopBound(SIZE); i += S_SPECIES.length()) {
            ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, i);
            av.blend(bv.blend(cv, mask), mask).intoArray(sr, i);
        }
    }

    @Benchmark
    public void nestedBlendInnerInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int i = 0; i < I_SPECIES.loopBound(SIZE); i += I_SPECIES.length()) {
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            av.blend(bv.blend(cv, mask), mask).intoArray(ir, i);
        }
    }

    @Benchmark
    public void nestedBlendInnerLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, mask_arr, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        for (int i = 0; i < L_SPECIES.loopBound(SIZE); i += L_SPECIES.length()) {
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            av.blend(bv.blend(cv, mask), mask).intoArray(lr, i);
        }
    }

    @Benchmark
    public void nestedBlendInnerFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, mask_arr, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        for (int i = 0; i < F_SPECIES.loopBound(SIZE); i += F_SPECIES.length()) {
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            av.blend(bv.blend(cv, mask), mask).intoArray(fr, i);
        }
    }

    @Benchmark
    public void nestedBlendInnerDouble() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, mask_arr, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, 0);
        for (int i = 0; i < D_SPECIES.loopBound(SIZE); i += D_SPECIES.length()) {
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            av.blend(bv.blend(cv, mask), mask).intoArray(dr, i);
        }
    }

    // Pattern: VectorBlend(a, b, NOT(m)) => VectorBlend(b, a, m)

    @Benchmark
    public void blendNegatedMaskByte() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        for (int j = 0; j < B_SPECIES.loopBound(SIZE); j += B_SPECIES.length()) {
            VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, mask_arr, j);
            av.blend(bv, mask.not()).intoArray(br, j);
        }
    }

    @Benchmark
    public void blendNegatedMaskShort() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        for (int j = 0; j < S_SPECIES.loopBound(SIZE); j += S_SPECIES.length()) {
            VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, mask_arr, j);
            av.blend(bv, mask.not()).intoArray(sr, j);
        }
    }

    @Benchmark
    public void blendNegatedMaskInt() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        for (int j = 0; j < I_SPECIES.loopBound(SIZE); j += I_SPECIES.length()) {
            VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, j);
            av.blend(bv, mask.not()).intoArray(ir, j);
        }
    }

    @Benchmark
    public void blendNegatedMaskLong() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        for (int j = 0; j < L_SPECIES.loopBound(SIZE); j += L_SPECIES.length()) {
            VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, mask_arr, j);
            av.blend(bv, mask.not()).intoArray(lr, j);
        }
    }

    @Benchmark
    public void blendNegatedMaskFloat() {
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        for (int j = 0; j < F_SPECIES.loopBound(SIZE); j += F_SPECIES.length()) {
            VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, mask_arr, j);
            av.blend(bv, mask.not()).intoArray(fr, j);
        }
    }

    @Benchmark
    public void blendNegatedMaskDouble() {
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        for (int j = 0; j < D_SPECIES.loopBound(SIZE); j += D_SPECIES.length()) {
            VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, mask_arr, j);
            av.blend(bv, mask.not()).intoArray(dr, j);
        }
    }
}
