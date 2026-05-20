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
@State(Scope.Thread)
@Fork(value = 2, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorBlendOpts {
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    @Param({"1024"})
    private int ARRAYLEN;

    @Param({"256"})
    private int COUNT;

    private boolean[] mask_arr;
    private int[] ia, ib, ic, ir;
    private long[] la, lb, lc, lr;
    private float[] fa, fb, fc, fr;
    private double[] da, db, dc, dr;

    @Setup(Level.Trial)
    public void init() {
        Random r = new Random(1024);
        mask_arr = new boolean[ARRAYLEN];
        ia = new int[ARRAYLEN];
        ib = new int[ARRAYLEN];
        ic = new int[ARRAYLEN];
        ir = new int[ARRAYLEN];
        la = new long[ARRAYLEN];
        lb = new long[ARRAYLEN];
        lc = new long[ARRAYLEN];
        lr = new long[ARRAYLEN];
        fa = new float[ARRAYLEN];
        fb = new float[ARRAYLEN];
        fc = new float[ARRAYLEN];
        fr = new float[ARRAYLEN];
        da = new double[ARRAYLEN];
        db = new double[ARRAYLEN];
        dc = new double[ARRAYLEN];
        dr = new double[ARRAYLEN];
        for (int i = 0; i < ARRAYLEN; i++) {
            mask_arr[i] = r.nextBoolean();
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
    public void identityAllOnesInt() {
        VectorMask<Integer> allTrue = I_SPECIES.maskAll(true);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        for (int j = 0; j < COUNT; j++) {
            av = av.blend(bv, allTrue).lanewise(VectorOperators.ADD, av);
        }
        av.intoArray(ir, 0);
    }

    // VectorBlend(a, b, AllZerosMask) => a
    @Benchmark
    public void identityAllZerosLong() {
        VectorMask<Long> allFalse = L_SPECIES.maskAll(false);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        for (int j = 0; j < COUNT; j++) {
            bv = av.blend(bv, allFalse).lanewise(VectorOperators.ADD, bv);
        }
        bv.intoArray(lr, 0);
    }

    // VectorBlend(VectorBlend(a, b, m), c, m) => VectorBlend(a, c, m)
    @Benchmark
    public void nestedBlendOuterInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            bv = av.blend(bv, mask).blend(cv, mask);
        }
        bv.intoArray(ir, 0);
    }

    // VectorBlend(a, VectorBlend(b, c, m), m) => VectorBlend(a, c, m)
    @Benchmark
    public void nestedBlendInnerLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, mask_arr, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        for (int j = 0; j < COUNT; j++) {
            bv = av.blend(bv.blend(cv, mask), mask);
        }
        bv.intoArray(lr, 0);
    }

    // VectorBlend(a, b, NOT(m)) => VectorBlend(b, a, m)
    @Benchmark
    public void blendNegatedMaskInt() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        for (int j = 0; j < I_SPECIES.loopBound(ARRAYLEN); j += I_SPECIES.length()) {
            VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, j);
            av.blend(bv, mask.not()).intoArray(ir, j);
        }
    }

    // VectorBlend(AndV(a, c), AndV(b, c), m) => AndV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorAndInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.AND, cv)
                   .blend(bv.lanewise(VectorOperators.AND, cv), mask);
        }
        cv.intoArray(ir, 0);
    }

    // VectorBlend(OrV(a, c), OrV(b, c), m) => OrV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorOrLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, mask_arr, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.OR, cv)
                   .blend(bv.lanewise(VectorOperators.OR, cv), mask);
        }
        cv.intoArray(lr, 0);
    }

    // VectorBlend(XorV(a, c), XorV(b, c), m) => XorV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorXorInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.XOR, cv)
                   .blend(bv.lanewise(VectorOperators.XOR, cv), mask);
        }
        cv.intoArray(ir, 0);
    }

    // VectorBlend(AddV(a, c), AddV(b, c), m) => AddV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorAddInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.ADD, cv)
                   .blend(bv.lanewise(VectorOperators.ADD, cv), mask);
        }
        cv.intoArray(ir, 0);
    }

    // VectorBlend(MulV(a, c), MulV(b, c), m) => MulV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorMulLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, mask_arr, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.MUL, cv)
                   .blend(bv.lanewise(VectorOperators.MUL, cv), mask);
        }
        cv.intoArray(lr, 0);
    }

    // VectorBlend(SubV(a, c), SubV(b, c), m) => SubV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorSubInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.SUB, cv)
                   .blend(bv.lanewise(VectorOperators.SUB, cv), mask);
        }
        cv.intoArray(ir, 0);
    }

    // VectorBlend(LShiftV(a, c), LShiftV(b, c), m)
    //   => LShiftV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorLShiftInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.LSHL, cv)
                   .blend(bv.lanewise(VectorOperators.LSHL, cv), mask);
        }
        cv.intoArray(ir, 0);
    }

    // VectorBlend(RotateLeftV(a, c), RotateLeftV(b, c), m)
    //   => RotateLeftV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorRolInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.ROL, cv)
                   .blend(bv.lanewise(VectorOperators.ROL, cv), mask);
        }
        cv.intoArray(ir, 0);
    }

    // VectorBlend(CompressBitsV(a, c), CompressBitsV(b, c), m)
    //   => CompressBitsV(VectorBlend(a, b, m), c)
    // Only intrinsified on AArch64 SVE2 + svebitperm; elsewhere VectorAPI falls
    // back to a scalar Integer.compress loop and the optimization is moot.
    @Benchmark
    public void factorCompressBitsInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.COMPRESS_BITS, cv)
                   .blend(bv.lanewise(VectorOperators.COMPRESS_BITS, cv), mask);
        }
        cv.intoArray(ir, 0);
    }

    // VectorBlend(ExpandBitsV(a, c), ExpandBitsV(b, c), m)
    //   => ExpandBitsV(VectorBlend(a, b, m), c)
    // Same AArch64 SVE2 + svebitperm caveat as factorCompressBitsInt.
    @Benchmark
    public void factorExpandBitsLong() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, mask_arr, 0);
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        LongVector cv = LongVector.fromArray(L_SPECIES, lc, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.EXPAND_BITS, cv)
                   .blend(bv.lanewise(VectorOperators.EXPAND_BITS, cv), mask);
        }
        cv.intoArray(lr, 0);
    }

    // VectorBlend(MinV(a, c), MinV(b, c), m) => MinV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorMinFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, mask_arr, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.MIN, cv)
                   .blend(bv.lanewise(VectorOperators.MIN, cv), mask);
        }
        cv.intoArray(fr, 0);
    }

    // VectorBlend(MaxV(a, c), MaxV(b, c), m) => MaxV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorMaxDouble() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, mask_arr, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.MAX, cv)
                   .blend(bv.lanewise(VectorOperators.MAX, cv), mask);
        }
        cv.intoArray(dr, 0);
    }

    // VectorBlend(AddVF(a, c), AddVF(b, c), m) => AddVF(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorAddFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, mask_arr, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.ADD, cv)
                   .blend(bv.lanewise(VectorOperators.ADD, cv), mask);
        }
        cv.intoArray(fr, 0);
    }

    // VectorBlend(SubVD(a, c), SubVD(b, c), m) => SubVD(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorSubDouble() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, mask_arr, 0);
        DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, 0);
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.SUB, cv)
                   .blend(bv.lanewise(VectorOperators.SUB, cv), mask);
        }
        cv.intoArray(dr, 0);
    }

    // VectorBlend(DivVF(a, c), DivVF(b, c), m) => DivVF(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorDivFloat() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, mask_arr, 0);
        FloatVector av = FloatVector.fromArray(F_SPECIES, fa, 0);
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.DIV, cv)
                   .blend(bv.lanewise(VectorOperators.DIV, cv), mask);
        }
        cv.intoArray(fr, 0);
    }

    // VectorBlend(SaturatingAddV(a, c), SaturatingAddV(b, c), m)
    //   => SaturatingAddV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorSAddInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.SADD, cv)
                   .blend(bv.lanewise(VectorOperators.SADD, cv), mask);
        }
        cv.intoArray(ir, 0);
    }

    // VectorBlend(SaturatingSubV(a, c), SaturatingSubV(b, c), m)
    //   => SaturatingSubV(VectorBlend(a, b, m), c)
    @Benchmark
    public void factorSSubInt() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, mask_arr, 0);
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        IntVector cv = IntVector.fromArray(I_SPECIES, ic, 0);
        for (int j = 0; j < COUNT; j++) {
            cv = av.lanewise(VectorOperators.SSUB, cv)
                   .blend(bv.lanewise(VectorOperators.SSUB, cv), mask);
        }
        cv.intoArray(ir, 0);
    }
}
