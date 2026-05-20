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

    @Param({"1024"})
    private int ARRAYLEN;

    @Param({"256"})
    private int COUNT;

    private boolean[] mask_arr;
    private int[] ia, ib, ic, ir;
    private long[] la, lb, lc, lr;

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
        for (int i = 0; i < ARRAYLEN; i++) {
            mask_arr[i] = r.nextBoolean();
            ia[i] = r.nextInt();
            ib[i] = r.nextInt();
            ic[i] = r.nextInt();
            la[i] = r.nextLong();
            lb[i] = r.nextLong();
            lc[i] = r.nextLong();
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
}
