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

package org.openjdk.bench.jdk.incubator.vector;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(jvmArgs = { "--add-modules=jdk.incubator.vector" })
public class MaskCompareNotBenchmark {
    @Param({"4096"})
    private int ARRAYLEN;
    private static Random r = new Random();

    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    boolean[] mr;
    byte[] ba;
    byte[] bb;
    short[] sa;
    short[] sb;
    int[] ia;
    int[] ib;
    long[] la;
    long[] lb;
    float[] fa;
    float[] fb;
    double[] da;
    double[] db;

    @Setup
    public void init() {
        mr = new boolean[ARRAYLEN];
        ba = new byte[ARRAYLEN];
        bb = new byte[ARRAYLEN];
        sa = new short[ARRAYLEN];
        sb = new short[ARRAYLEN];
        ia = new int[ARRAYLEN];
        ib = new int[ARRAYLEN];
        la = new long[ARRAYLEN];
        lb = new long[ARRAYLEN];
        fa = new float[ARRAYLEN];
        fb = new float[ARRAYLEN];
        da = new double[ARRAYLEN];
        db = new double[ARRAYLEN];

        for (int i = 0; i < ARRAYLEN; i++) {
            mr[i] = r.nextBoolean();
            ba[i] = (byte) r.nextInt();
            bb[i] = (byte) r.nextInt();
            sa[i] = (short) r.nextInt();
            sb[i] = (short) r.nextInt();
            ia[i] = r.nextInt();
            ib[i] = r.nextInt();
            la[i] = r.nextLong();
            lb[i] = r.nextLong();
            fa[i] = r.nextFloat();
            fb[i] = r.nextFloat();
            da[i] = r.nextDouble();
            db[i] = r.nextDouble();
        }
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private void testCompareMaskNotByte(VectorOperators.Comparison op) {
        ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
        for (int j = 0; j < ARRAYLEN; j += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, ba, j);
            VectorMask<Byte> m = av.compare(op, bv).not();
            m.intoArray(mr, j);
        }
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private void testCompareMaskNotShort(VectorOperators.Comparison op) {
        ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
        for (int j = 0; j < ARRAYLEN; j += S_SPECIES.length()) {
            ShortVector av = ShortVector.fromArray(S_SPECIES, sa, j);
            VectorMask<Short> m = av.compare(op, bv).not();
            m.intoArray(mr, j);
        }
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private void testCompareMaskNotInt(VectorOperators.Comparison op) {
        IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
        for (int j = 0; j < ARRAYLEN; j += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, j);
            VectorMask<Integer> m = av.compare(op, bv).not();
            m.intoArray(mr, j);
        }
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private void testCompareMaskNotLong(VectorOperators.Comparison op) {
        LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
        for (int j = 0; j < ARRAYLEN; j += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, j);
            VectorMask<Long> m = av.compare(op, bv).not();
            m.intoArray(mr, j);
        }
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private void testCompareMaskNotFloat(VectorOperators.Comparison op) {
        FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
        for (int j = 0; j < ARRAYLEN; j += F_SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(F_SPECIES, fa, j);
            VectorMask<Float> m = av.compare(op, bv).not();
            m.intoArray(mr, j);
        }
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private void testCompareMaskNotDouble(VectorOperators.Comparison op) {
        DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
        for (int j = 0; j < ARRAYLEN; j += D_SPECIES.length()) {
            DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, j);
            VectorMask<Double> m = av.compare(op, bv).not();
            m.intoArray(mr, j);
        }
    }

    @Benchmark
    public void testCompareEQMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.EQ);
    }

    @Benchmark
    public void testCompareNEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.NE);
    }

    @Benchmark
    public void testCompareLTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.LT);
    }

    @Benchmark
    public void testCompareGTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.GT);
    }

    @Benchmark
    public void testCompareLEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.LE);
    }

    @Benchmark
    public void testCompareGEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.GE);
    }

    @Benchmark
    public void testCompareULTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.ULT);
    }

    @Benchmark
    public void testCompareUGTMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.UGT);
    }

    @Benchmark
    public void testCompareULEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.ULE);
    }

    @Benchmark
    public void testCompareUGEMaskNotByte() {
        testCompareMaskNotByte(VectorOperators.UGE);
    }

    @Benchmark
    public void testCompareEQMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.EQ);
    }

    @Benchmark
    public void testCompareNEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.NE);
    }

    @Benchmark
    public void testCompareLTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.LT);
    }

    @Benchmark
    public void testCompareGTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.GT);
    }

    @Benchmark
    public void testCompareLEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.LE);
    }

    @Benchmark
    public void testCompareGEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.GE);
    }

    @Benchmark
    public void testCompareULTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.ULT);
    }

    @Benchmark
    public void testCompareUGTMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.UGT);
    }

    @Benchmark
    public void testCompareULEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.ULE);
    }

    @Benchmark
    public void testCompareUGEMaskNotShort() {
        testCompareMaskNotShort(VectorOperators.UGE);
    }

    @Benchmark
    public void testCompareEQMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.EQ);
    }

    @Benchmark
    public void testCompareNEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.NE);
    }

    @Benchmark
    public void testCompareLTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.LT);
    }

    @Benchmark
    public void testCompareGTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.GT);
    }

    @Benchmark
    public void testCompareLEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.LE);
    }

    @Benchmark
    public void testCompareGEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.GE);
    }

    @Benchmark
    public void testCompareULTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.ULT);
    }

    @Benchmark
    public void testCompareUGTMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.UGT);
    }

    @Benchmark
    public void testCompareULEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.ULE);
    }

    @Benchmark
    public void testCompareUGEMaskNotInt() {
        testCompareMaskNotInt(VectorOperators.UGE);
    }

    @Benchmark
    public void testCompareEQMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.EQ);
    }

    @Benchmark
    public void testCompareNEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.NE);
    }

    @Benchmark
    public void testCompareLTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.LT);
    }

    @Benchmark
    public void testCompareGTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.GT);
    }

    @Benchmark
    public void testCompareLEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.LE);
    }

    @Benchmark
    public void testCompareGEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.GE);
    }

    @Benchmark
    public void testCompareULTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.ULT);
    }

    @Benchmark
    public void testCompareUGTMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.UGT);
    }

    @Benchmark
    public void testCompareULEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.ULE);
    }

    @Benchmark
    public void testCompareUGEMaskNotLong() {
        testCompareMaskNotLong(VectorOperators.UGE);
    }

    @Benchmark
    public void testCompareEQMaskNotFloat() {
        testCompareMaskNotFloat(VectorOperators.EQ);
    }

    @Benchmark
    public void testCompareNEMaskNotFloat() {
        testCompareMaskNotFloat(VectorOperators.NE);
    }

    @Benchmark
    public void testCompareEQMaskNotDouble() {
        testCompareMaskNotDouble(VectorOperators.EQ);
    }

    @Benchmark
    public void testCompareNEMaskNotDouble() {
        testCompareMaskNotDouble(VectorOperators.NE);
    }
}
