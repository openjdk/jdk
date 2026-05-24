/*
 *  Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.jdk.incubator.vector;

import java.util.concurrent.TimeUnit;
import java.util.Random;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorSliceBenchmark {
    @Param({"1024", "2047", "4096"})
    int size;

    byte [] bsrc1;
    byte [] bsrc2;
    byte [] bdst;

    short [] ssrc1;
    short [] ssrc2;
    short [] sdst;

    int [] isrc1;
    int [] isrc2;
    int [] idst;

    long [] lsrc1;
    long [] lsrc2;
    long [] ldst;

    float [] fsrc1;
    float [] fsrc2;
    float [] fdst;

    double [] dsrc1;
    double [] dsrc2;
    double [] ddst;

    static final VectorSpecies<Byte> bspecies    = ByteVector.SPECIES_PREFERRED;
    static final VectorSpecies<Short> sspecies   = ShortVector.SPECIES_PREFERRED;
    static final VectorSpecies<Integer> ispecies = IntVector.SPECIES_PREFERRED;
    static final VectorSpecies<Long> lspecies    = LongVector.SPECIES_PREFERRED;
    static final VectorSpecies<Float> fspecies = FloatVector.SPECIES_PREFERRED;
    static final VectorSpecies<Double> dspecies    = DoubleVector.SPECIES_PREFERRED;

    static final VectorMask<Byte> bmask = VectorMask.fromLong(bspecies, 0xF);
    static final VectorMask<Short> smask = VectorMask.fromLong(sspecies, 0xF);
    static final VectorMask<Integer> imask = VectorMask.fromLong(ispecies, 0xF);
    static final VectorMask<Long> lmask = VectorMask.fromLong(lspecies, 0xF);
    static final VectorMask<Float> fmask = VectorMask.fromLong(fspecies, 0xF);
    static final VectorMask<Double> dmask = VectorMask.fromLong(dspecies, 0xF);

    static final int B_SLICE_IDX1 = bspecies.length() / 2;
    static final int B_SLICE_IDX2 = bspecies.length() / 4;

    static final int S_SLICE_IDX1 = sspecies.length() / 2;
    static final int S_SLICE_IDX2 = sspecies.length() / 4;

    static final int I_SLICE_IDX1 = ispecies.length() / 2;
    static final int I_SLICE_IDX2 = ispecies.length() / 4;

    static final int L_SLICE_IDX1 = lspecies.length() / 2;
    static final int L_SLICE_IDX2 = lspecies.length() / 4;

    static final int F_SLICE_IDX1 = ispecies.length() / 2;
    static final int F_SLICE_IDX2 = ispecies.length() / 4;

    static final int D_SLICE_IDX1 = lspecies.length() / 2;
    static final int D_SLICE_IDX2 = lspecies.length() / 4;

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(2048);

        bsrc1 = new byte[size];
        bsrc2 = new byte[size];
        bdst  = new byte[size];

        ssrc1 = new short[size];
        ssrc2 = new short[size];
        sdst  = new short[size];

        isrc1 = new int[size];
        isrc2 = new int[size];
        idst  = new int[size];

        lsrc1 = new long[size];
        lsrc2 = new long[size];
        ldst  = new long[size];

        fsrc1 = new float[size];
        fsrc2 = new float[size];
        fdst  = new float[size];

        dsrc1 = new double[size];
        dsrc2 = new double[size];
        ddst  = new double[size];

        for (int i = 0; i < size; i++) {
            bsrc1[i] = (byte)r.nextInt(size);
            bsrc2[i] = (byte)r.nextInt(size);

            ssrc1[i] = (short)r.nextInt(size);
            ssrc2[i] = (short)r.nextInt(size);

            isrc1[i] = r.nextInt(size);
            isrc2[i] = r.nextInt(size);

            lsrc1[i] = r.nextLong(size);
            lsrc2[i] = r.nextLong(size);

            fsrc1[i] = r.nextFloat(size);
            fsrc2[i] = r.nextFloat(size);

            dsrc1[i] = r.nextDouble(size);
            dsrc2[i] = r.nextDouble(size);
        }
    }

    @Benchmark
    public void byteVectorSliceWithConstantIndex1() {
        for (int i = 0; i < bspecies.loopBound(bdst.length); i += bspecies.length()) {
            ByteVector.fromArray(bspecies, bsrc1, i)
                      .slice(B_SLICE_IDX1, ByteVector.fromArray(bspecies, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Benchmark
    public void byteVectorSliceWithConstantIndex2Masked() {
        for (int i = 0; i < bspecies.loopBound(bdst.length); i += bspecies.length()) {
            ByteVector.fromArray(bspecies, bsrc1, i)
                      .slice(B_SLICE_IDX2, ByteVector.fromArray(bspecies, bsrc2, i), bmask)
                      .intoArray(bdst, i);
        }
    }

    @Benchmark
    public void byteVectorSliceWithVariableIndex() {
        for (int i = 0; i < bspecies.loopBound(bdst.length); i += bspecies.length()) {
            ByteVector.fromArray(bspecies, bsrc1, i)
                      .slice(i & (bspecies.length() - 1), ByteVector.fromArray(bspecies, bsrc2, i))
                      .intoArray(bdst, i);
        }
    }

    @Benchmark
    public void shortVectorSliceWithConstantIndex1() {
        for (int i = 0; i < sspecies.loopBound(sdst.length); i += sspecies.length()) {
            ShortVector.fromArray(sspecies, ssrc1, i)
                      .slice(S_SLICE_IDX1, ShortVector.fromArray(sspecies, ssrc2, i))
                      .intoArray(sdst, i);
        }
    }

    @Benchmark
    public void shortVectorSliceWithConstantIndex2Masked() {
        for (int i = 0; i < sspecies.loopBound(sdst.length); i += sspecies.length()) {
            ShortVector.fromArray(sspecies, ssrc1, i)
                      .slice(S_SLICE_IDX2, ShortVector.fromArray(sspecies, ssrc2, i), smask)
                      .intoArray(sdst, i);
        }
    }

    @Benchmark
    public void shortVectorSliceWithVariableIndex() {
        for (int i = 0; i < sspecies.loopBound(sdst.length); i += sspecies.length()) {
            ShortVector.fromArray(sspecies, ssrc1, i)
                      .slice(i & (sspecies.length() - 1), ShortVector.fromArray(sspecies, ssrc2, i))
                      .intoArray(sdst, i);
        }
    }

    @Benchmark
    public void intVectorSliceWithConstantIndex1() {
        for (int i = 0; i < ispecies.loopBound(idst.length); i += ispecies.length()) {
            IntVector.fromArray(ispecies, isrc1, i)
                      .slice(I_SLICE_IDX1, IntVector.fromArray(ispecies, isrc2, i))
                      .intoArray(idst, i);
        }
    }

    @Benchmark
    public void intVectorSliceWithConstantIndex2Masked() {
        for (int i = 0; i < ispecies.loopBound(idst.length); i += ispecies.length()) {
            IntVector.fromArray(ispecies, isrc1, i)
                     .slice(I_SLICE_IDX2, IntVector.fromArray(ispecies, isrc2, i), imask)
                     .intoArray(idst, i);
        }
    }

    @Benchmark
    public void intVectorSliceWithVariableIndex() {
        for (int i = 0; i < ispecies.loopBound(idst.length); i += ispecies.length()) {
            IntVector.fromArray(ispecies, isrc1, i)
                      .slice(i & (ispecies.length() - 1), IntVector.fromArray(ispecies, isrc2, i))
                      .intoArray(idst, i);
        }
    }

    @Benchmark
    public void floatVectorSliceWithConstantIndex1() {
        for (int i = 0; i < fspecies.loopBound(fdst.length); i += fspecies.length()) {
            FloatVector.fromArray(fspecies, fsrc1, i)
                       .slice(F_SLICE_IDX1, FloatVector.fromArray(fspecies, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Benchmark
    public void floatVectorSliceWithConstantIndex2Masked() {
        for (int i = 0; i < fspecies.loopBound(fdst.length); i += fspecies.length()) {
            FloatVector.fromArray(fspecies, fsrc1, i)
                      .slice(F_SLICE_IDX2, FloatVector.fromArray(fspecies, fsrc2, i), fmask)
                      .intoArray(fdst, i);
        }
    }

    @Benchmark
    public void floatVectorSliceWithVariableIndex() {
        for (int i = 0; i < fspecies.loopBound(fdst.length); i += fspecies.length()) {
            FloatVector.fromArray(fspecies, fsrc1, i)
                       .slice(i & (fspecies.length() - 1), FloatVector.fromArray(fspecies, fsrc2, i))
                       .intoArray(fdst, i);
        }
    }

    @Benchmark
    public void longVectorSliceWithConstantIndex1() {
        for (int i = 0; i < lspecies.loopBound(ldst.length); i += lspecies.length()) {
            LongVector.fromArray(lspecies, lsrc1, i)
                      .slice(L_SLICE_IDX1, LongVector.fromArray(lspecies, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Benchmark
    public void longVectorSliceWithConstantIndex2Masked() {
        for (int i = 0; i < lspecies.loopBound(ldst.length); i += lspecies.length()) {
            LongVector.fromArray(lspecies, lsrc1, i)
                      .slice(L_SLICE_IDX2, LongVector.fromArray(lspecies, lsrc2, i), lmask)
                      .intoArray(ldst, i);
        }
    }

    @Benchmark
    public void longVectorSliceWithVariableIndex() {
        for (int i = 0; i < lspecies.loopBound(ldst.length); i += lspecies.length()) {
            LongVector.fromArray(lspecies, lsrc1, i)
                      .slice(i & (lspecies.length() - 1), LongVector.fromArray(lspecies, lsrc2, i))
                      .intoArray(ldst, i);
        }
    }

    @Benchmark
    public void doubleVectorSliceWithConstantIndex1() {
        for (int i = 0; i < dspecies.loopBound(ddst.length); i += dspecies.length()) {
            DoubleVector.fromArray(dspecies, dsrc1, i)
                        .slice(D_SLICE_IDX1, DoubleVector.fromArray(dspecies, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }

    @Benchmark
    public void doubleVectorSliceWithConstantIndex2Masked() {
        for (int i = 0; i < dspecies.loopBound(ddst.length); i += dspecies.length()) {
            DoubleVector.fromArray(dspecies, dsrc1, i)
                        .slice(D_SLICE_IDX2, DoubleVector.fromArray(dspecies, dsrc2, i), dmask)
                        .intoArray(ddst, i);
        }
    }

    @Benchmark
    public void doubleVectorSliceWithVariableIndex() {
        for (int i = 0; i < dspecies.loopBound(ddst.length); i += dspecies.length()) {
            DoubleVector.fromArray(dspecies, dsrc1, i)
                        .slice(i & (dspecies.length() - 1), DoubleVector.fromArray(dspecies, dsrc2, i))
                        .intoArray(ddst, i);
        }
    }
}
