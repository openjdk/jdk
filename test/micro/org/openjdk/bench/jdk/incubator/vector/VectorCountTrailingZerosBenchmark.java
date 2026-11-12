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
package org.openjdk.bench.jdk.incubator.vector;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = {"--add-modules=jdk.incubator.vector"})
@State(Scope.Thread)
public class VectorCountTrailingZerosBenchmark {
    static final int VECTORS = 256;
    static final int CHAIN = 1024;

    byte[] srcB, dstB;
    short[] srcS, dstS;
    int[] srcI, dstI;
    long[] srcL, dstL;

    @Setup
    public void setup() {
        Random r = new Random(1);
        srcB = new byte[VECTORS * ByteVector.SPECIES_PREFERRED.length()];
        dstB = new byte[srcB.length];
        r.nextBytes(srcB);
        srcS = new short[VECTORS * ShortVector.SPECIES_PREFERRED.length()];
        dstS = new short[srcS.length];
        srcI = new int[VECTORS * IntVector.SPECIES_PREFERRED.length()];
        dstI = new int[srcI.length];
        srcL = new long[VECTORS * LongVector.SPECIES_PREFERRED.length()];
        dstL = new long[srcL.length];
        for (int i = 0; i < srcS.length; i++) {
            srcS[i] = (short) r.nextInt();
        }
        for (int i = 0; i < srcI.length; i++) {
            srcI[i] = r.nextInt();
        }
        for (int i = 0; i < srcL.length; i++) {
            srcL[i] = r.nextLong();
        }
    }

    @Benchmark
    @OperationsPerInvocation(VECTORS)
    public void byteThroughput(Blackhole bh) {
        int lanes = ByteVector.SPECIES_PREFERRED.length();
        for (int i = 0; i < VECTORS; i++) {
            ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, srcB, i * lanes)
                    .lanewise(VectorOperators.TRAILING_ZEROS_COUNT)
                    .intoArray(dstB, i * lanes);
        }
        bh.consume(dstB);
    }

    @Benchmark
    @OperationsPerInvocation(VECTORS)
    public void shortThroughput(Blackhole bh) {
        int lanes = ShortVector.SPECIES_PREFERRED.length();
        for (int i = 0; i < VECTORS; i++) {
            ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, srcS, i * lanes)
                    .lanewise(VectorOperators.TRAILING_ZEROS_COUNT)
                    .intoArray(dstS, i * lanes);
        }
        bh.consume(dstS);
    }

    @Benchmark
    @OperationsPerInvocation(VECTORS)
    public void intThroughput(Blackhole bh) {
        int lanes = IntVector.SPECIES_PREFERRED.length();
        for (int i = 0; i < VECTORS; i++) {
            IntVector.fromArray(IntVector.SPECIES_PREFERRED, srcI, i * lanes)
                    .lanewise(VectorOperators.TRAILING_ZEROS_COUNT)
                    .intoArray(dstI, i * lanes);
        }
        bh.consume(dstI);
    }

    @Benchmark
    @OperationsPerInvocation(VECTORS)
    public void longThroughput(Blackhole bh) {
        int lanes = LongVector.SPECIES_PREFERRED.length();
        for (int i = 0; i < VECTORS; i++) {
            LongVector.fromArray(LongVector.SPECIES_PREFERRED, srcL, i * lanes)
                    .lanewise(VectorOperators.TRAILING_ZEROS_COUNT)
                    .intoArray(dstL, i * lanes);
        }
        bh.consume(dstL);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @OperationsPerInvocation(CHAIN)
    public void byteLatency(Blackhole bh) {
        ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, srcB, 0);
        for (int i = 0; i < CHAIN; i++) {
            v = v.lanewise(VectorOperators.TRAILING_ZEROS_COUNT);
        }
        bh.consume(v.reduceLanes(VectorOperators.XOR));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @OperationsPerInvocation(CHAIN)
    public void shortLatency(Blackhole bh) {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, srcS, 0);
        for (int i = 0; i < CHAIN; i++) {
            v = v.lanewise(VectorOperators.TRAILING_ZEROS_COUNT);
        }
        bh.consume(v.reduceLanes(VectorOperators.XOR));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @OperationsPerInvocation(CHAIN)
    public void intLatency(Blackhole bh) {
        IntVector v = IntVector.fromArray(IntVector.SPECIES_PREFERRED, srcI, 0);
        for (int i = 0; i < CHAIN; i++) {
            v = v.lanewise(VectorOperators.TRAILING_ZEROS_COUNT);
        }
        bh.consume(v.reduceLanes(VectorOperators.XOR));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @OperationsPerInvocation(CHAIN)
    public void longLatency(Blackhole bh) {
        LongVector v = LongVector.fromArray(LongVector.SPECIES_PREFERRED, srcL, 0);
        for (int i = 0; i < CHAIN; i++) {
            v = v.lanewise(VectorOperators.TRAILING_ZEROS_COUNT);
        }
        bh.consume(v.reduceLanes(VectorOperators.XOR));
    }
}
