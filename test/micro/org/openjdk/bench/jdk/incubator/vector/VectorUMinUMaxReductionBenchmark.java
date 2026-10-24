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
import org.openjdk.jmh.infra.*;

import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "--add-modules=jdk.incubator.vector" })
public class VectorUMinUMaxReductionBenchmark {
    @Param({"1024"})
    private int size;

    private static final VectorSpecies<Byte> B_SPECIES = VectorSpecies.ofLargestShape(byte.class);
    private static final VectorSpecies<Short> S_SPECIES = VectorSpecies.ofLargestShape(short.class);
    private static final VectorSpecies<Integer> I_SPECIES = VectorSpecies.ofLargestShape(int.class);
    private static final VectorSpecies<Long> L_SPECIES = VectorSpecies.ofLargestShape(long.class);

    private Random r = new Random();
    private byte[] ba;
    private short[] sa;
    private int[] ia;
    private long[] la;
    private boolean[] ma;

    @Setup
    public void init() {
        ba = new byte[size];
        sa = new short[size];
        ia = new int[size];
        la = new long[size];
        ma = new boolean[size];

        for (int i = 0; i < size; i++) {
            ba[i] = (byte) r.nextInt();
            sa[i] = (short) r.nextInt();
            ia[i] = r.nextInt();
            la[i] = r.nextLong();
            ma[i] = r.nextInt() % 2 == 0;
        }
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private byte byteUMinUMaxReductionKernel(VectorOperators.Associative op) {
        byte res = 0;
        for (int i = 0; i < B_SPECIES.loopBound(size); i += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, ba, i);
            res += av.reduceLanes(op);
        }
        return res;
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private byte byteUMinUMaxReductionMaskedKernel(VectorOperators.Associative op) {
        byte res = 0;
        VectorMask<Byte> m = VectorMask.fromArray(B_SPECIES, ma, 0);
        for (int i = 0; i < B_SPECIES.loopBound(size); i += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, ba, i);
            res += av.reduceLanes(op, m);
        }
        return res;
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private short shortUMinUMaxReductionKernel(VectorOperators.Associative op) {
        short res = 0;
        for (int i = 0; i < S_SPECIES.loopBound(size); i += S_SPECIES.length()) {
            ShortVector av = ShortVector.fromArray(S_SPECIES, sa, i);
            res += av.reduceLanes(op);
        }
        return res;
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private short shortUMinUMaxReductionMaskedKernel(VectorOperators.Associative op) {
        short res = 0;
        VectorMask<Short> m = VectorMask.fromArray(S_SPECIES, ma, 0);
        for (int i = 0; i < S_SPECIES.loopBound(size); i += S_SPECIES.length()) {
            ShortVector av = ShortVector.fromArray(S_SPECIES, sa, i);
            res += av.reduceLanes(op, m);
        }
        return res;
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private int intUMinUMaxReductionKernel(VectorOperators.Associative op) {
        int res = 0;
        for (int i = 0; i < I_SPECIES.loopBound(size); i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            res += av.reduceLanes(op);
        }
        return res;
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private int intUMinUMaxReductionMaskedKernel(VectorOperators.Associative op) {
        int res = 0;
        VectorMask<Integer> m = VectorMask.fromArray(I_SPECIES, ma, 0);
        for (int i = 0; i < I_SPECIES.loopBound(size); i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            res += av.reduceLanes(op, m);
        }
        return res;
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private long longUMinUMaxReductionKernel(VectorOperators.Associative op) {
        long res = 0L;
        for (int i = 0; i < L_SPECIES.loopBound(size); i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            res += av.reduceLanes(op);
        }
        return res;
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private long longUMinUMaxReductionMaskedKernel(VectorOperators.Associative op) {
        long res = 0L;
        VectorMask<Long> m = VectorMask.fromArray(L_SPECIES, ma, 0);
        for (int i = 0; i < L_SPECIES.loopBound(size); i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            res += av.reduceLanes(op, m);
        }
        return res;
    }

    @Benchmark
    public byte byteUMinReduction() {
        return byteUMinUMaxReductionKernel(VectorOperators.UMIN);
    }

    @Benchmark
    public byte byteUMaxReduction() {
        return byteUMinUMaxReductionKernel(VectorOperators.UMAX);
    }

    @Benchmark
    public byte byteUMinReductionMasked() {
        return byteUMinUMaxReductionMaskedKernel(VectorOperators.UMIN);
    }

    @Benchmark
    public byte byteUMaxReductionMasked() {
        return byteUMinUMaxReductionMaskedKernel(VectorOperators.UMAX);
    }

    @Benchmark
    public short shortUMinReduction() {
        return shortUMinUMaxReductionKernel(VectorOperators.UMIN);
    }

    @Benchmark
    public short shortUMaxReduction() {
        return shortUMinUMaxReductionKernel(VectorOperators.UMAX);
    }

    @Benchmark
    public short shortUMinReductionMasked() {
        return shortUMinUMaxReductionMaskedKernel(VectorOperators.UMIN);
    }

    @Benchmark
    public short shortUMaxReductionMasked() {
        return shortUMinUMaxReductionMaskedKernel(VectorOperators.UMAX);
    }

    @Benchmark
    public int intUMinReduction() {
        return intUMinUMaxReductionKernel(VectorOperators.UMIN);
    }

    @Benchmark
    public int intUMaxReduction() {
        return intUMinUMaxReductionKernel(VectorOperators.UMAX);
    }

    @Benchmark
    public int intUMinReductionMasked() {
        return intUMinUMaxReductionMaskedKernel(VectorOperators.UMIN);
    }

    @Benchmark
    public int intUMaxReductionMasked() {
        return intUMinUMaxReductionMaskedKernel(VectorOperators.UMAX);
    }

    @Benchmark
    public long longUMinReduction() {
        return longUMinUMaxReductionKernel(VectorOperators.UMIN);
    }

    @Benchmark
    public long longUMaxReduction() {
        return longUMinUMaxReductionKernel(VectorOperators.UMAX);
    }

    @Benchmark
    public long longUMinReductionMasked() {
        return longUMinUMaxReductionMaskedKernel(VectorOperators.UMIN);
    }

    @Benchmark
    public long longUMaxReductionMasked() {
        return longUMinUMaxReductionMaskedKernel(VectorOperators.UMAX);
    }
}