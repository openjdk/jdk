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

import org.openjdk.jmh.annotations.*;
import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;

// Measures the Vector API not_and pattern (A & B) ^ B == ~A & B, which folds to
// a single BIC instruction on AArch64.

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "--add-modules=jdk.incubator.vector" })
public class LogicOperationsBenchmark {
    @Param({"1024"})
    private int size;

    private static final VectorSpecies<Byte> B_SPECIES = VectorSpecies.ofLargestShape(byte.class);
    private static final VectorSpecies<Short> S_SPECIES = VectorSpecies.ofLargestShape(short.class);
    private static final VectorSpecies<Integer> I_SPECIES = VectorSpecies.ofLargestShape(int.class);
    private static final VectorSpecies<Long> L_SPECIES = VectorSpecies.ofLargestShape(long.class);

    private Random r = new Random(42);

    private byte[]  ba, bb, bc;
    private short[] sa, sb, sc;
    private int[]   ia, ib, ic;
    private long[]  la, lb, lc;

    @Setup
    public void init() {
        ba = new byte[size];
        bb = new byte[size];
        bc = new byte[size];
        sa = new short[size];
        sb = new short[size];
        sc = new short[size];
        ia = new int[size];
        ib = new int[size];
        ic = new int[size];
        la = new long[size];
        lb = new long[size];
        lc = new long[size];

        for (int i = 0; i < size; i++) {
            ba[i] = (byte) r.nextInt();
            bb[i] = (byte) r.nextInt();
            sa[i] = (short) r.nextInt();
            sb[i] = (short) r.nextInt();
            ia[i] = r.nextInt();
            ib[i] = r.nextInt();
            la[i] = r.nextLong();
            lb[i] = r.nextLong();
        }
    }

    @Benchmark
    public void byteNotAnd() {
        ByteVector av = ByteVector.fromArray(B_SPECIES, ba, 0);
        for (int i = 0; i < B_SPECIES.loopBound(size); i += B_SPECIES.length()) {
            ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, i);
            av = av.and(bv).lanewise(VectorOperators.XOR, bv);
        }
        av.intoArray(bc, 0);
    }

    @Benchmark
    public void shortNotAnd() {
        ShortVector av = ShortVector.fromArray(S_SPECIES, sa, 0);
        for (int i = 0; i < S_SPECIES.loopBound(size); i += S_SPECIES.length()) {
            ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, i);
            av = av.and(bv).lanewise(VectorOperators.XOR, bv);
        }
        av.intoArray(sc, 0);
    }

    @Benchmark
    public void intNotAnd() {
        IntVector av = IntVector.fromArray(I_SPECIES, ia, 0);
        for (int i = 0; i < I_SPECIES.loopBound(size); i += I_SPECIES.length()) {
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            av = av.and(bv).lanewise(VectorOperators.XOR, bv);
        }
        av.intoArray(ic, 0);
    }

    @Benchmark
    public void longNotAnd() {
        LongVector av = LongVector.fromArray(L_SPECIES, la, 0);
        for (int i = 0; i < L_SPECIES.loopBound(size); i += L_SPECIES.length()) {
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            av = av.and(bv).lanewise(VectorOperators.XOR, bv);
        }
        av.intoArray(lc, 0);
    }
}
