/*
 * Copyright 2026 Arm Limited and/or its affiliates.
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

import java.util.Random;
import java.util.concurrent.TimeUnit;

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class LongVectorReduction {
    private static final VectorSpecies<Long> SPECIES_128 = LongVector.SPECIES_128;

    @Param({"512", "2048"})
    public int size;

    private long[] in1;
    private long[] in2;
    private long[] in3;
    private VectorMask<Long> mask;

    @Setup
    public void setup() {
        Random r = new Random(0);
        in1 = new long[size];
        in2 = new long[size];
        in3 = new long[size];
        mask = VectorMask.fromLong(SPECIES_128, 1);
        for (int i = 0; i < size; i++) {
            in1[i] = r.nextLong();
            in2[i] = r.nextLong();
            in3[i] = r.nextLong();
        }
    }

    @Benchmark
    public void addDotProduct(Blackhole bh) {
        LongVector acc = LongVector.zero(SPECIES_128);
        for (int i = 0; i < SPECIES_128.loopBound(size); i += SPECIES_128.length()) {
            LongVector a = LongVector.fromArray(SPECIES_128, in1, i);
            LongVector b = LongVector.fromArray(SPECIES_128, in2, i);
            acc = acc.add(a.mul(b));
        }
        long res = acc.reduceLanes(VectorOperators.ADD);
        bh.consume(res);
    }

    @Benchmark
    public void addDotProductShared(Blackhole bh) {
        LongVector acc = LongVector.zero(SPECIES_128);
        for (int i = 0; i < SPECIES_128.loopBound(size); i += SPECIES_128.length()) {
            LongVector a = LongVector.fromArray(SPECIES_128, in1, i);
            LongVector b = LongVector.fromArray(SPECIES_128, in2, i);
            LongVector c = LongVector.fromArray(SPECIES_128, in3, i);
            LongVector val = a.mul(b);
            acc = acc.add(val.add(val.mul(c)));
        }
        long res = acc.reduceLanes(VectorOperators.ADD);
        bh.consume(res);
    }

    @Benchmark
    public void subDotProduct(Blackhole bh) {
        LongVector acc = LongVector.zero(SPECIES_128);
        for (int i = 0; i < SPECIES_128.loopBound(size); i += SPECIES_128.length()) {
            LongVector a = LongVector.fromArray(SPECIES_128, in1, i);
            LongVector b = LongVector.fromArray(SPECIES_128, in2, i);
            acc = acc.sub(a.mul(b));
        }
        long res = acc.reduceLanes(VectorOperators.ADD);
        bh.consume(res);
    }

    @Benchmark
    public void addDotProductMasked(Blackhole bh) {
        LongVector acc = LongVector.zero(SPECIES_128);
        for (int i = 0; i < SPECIES_128.loopBound(size); i += SPECIES_128.length()) {
            LongVector a = LongVector.fromArray(SPECIES_128, in1, i);
            LongVector b = LongVector.fromArray(SPECIES_128, in2, i);
            acc = acc.add(a.mul(b), mask);
        }
        long res = acc.reduceLanes(VectorOperators.ADD);
        bh.consume(res);
    }

    @Benchmark
    public void subDotProductMasked(Blackhole bh) {
        LongVector acc = LongVector.zero(SPECIES_128);
        for (int i = 0; i < SPECIES_128.loopBound(size); i += SPECIES_128.length()) {
            LongVector a = LongVector.fromArray(SPECIES_128, in1, i);
            LongVector b = LongVector.fromArray(SPECIES_128, in2, i);
            acc = acc.sub(a.mul(b), mask);
        }
        long res = acc.reduceLanes(VectorOperators.ADD);
        bh.consume(res);
    }

    @Benchmark
    public void addBig(Blackhole bh) {
        LongVector acc = LongVector.zero(SPECIES_128);
        for (int i = 0; i < SPECIES_128.loopBound(size); i += SPECIES_128.length()) {
            LongVector a = LongVector.fromArray(SPECIES_128, in1, i);
            LongVector b = LongVector.fromArray(SPECIES_128, in2, i);
            LongVector c = LongVector.fromArray(SPECIES_128, in3, i);
            LongVector val = a.mul(b).add(a.mul(c)).add(b.mul(c));
            acc = acc.add(val);
        }
        long res = acc.reduceLanes(VectorOperators.ADD);
        bh.consume(res);
    }

    @Benchmark
    public void ifElsePhiAdd(Blackhole bh) {
        LongVector acc = LongVector.zero(SPECIES_128);
        for (int i = 0; i < SPECIES_128.loopBound(size); i += SPECIES_128.length()) {
            LongVector a = LongVector.fromArray(SPECIES_128, in1, i);
            LongVector b = LongVector.fromArray(SPECIES_128, in2, i);
            LongVector c = LongVector.fromArray(SPECIES_128, in3, i);
            LongVector selected;
            if ((i & SPECIES_128.length()) == 0) {
                selected = a.mul(b);
            } else {
                selected = b.mul(c);
            }
            acc = acc.add(selected.add(a.mul(c)));
        }
        long res = acc.reduceLanes(VectorOperators.ADD);
        bh.consume(res);
    }

    @Benchmark
    public void ifElsePhiSub(Blackhole bh) {
        LongVector acc = LongVector.zero(SPECIES_128);
        for (int i = 0; i < SPECIES_128.loopBound(size); i += SPECIES_128.length()) {
            LongVector a = LongVector.fromArray(SPECIES_128, in1, i);
            LongVector b = LongVector.fromArray(SPECIES_128, in2, i);
            LongVector c = LongVector.fromArray(SPECIES_128, in3, i);
            LongVector selected;
            if ((i & SPECIES_128.length()) == 0) {
                selected = a.mul(b);
            } else {
                selected = b.mul(c);
            }
            acc = acc.add(selected.sub(a.mul(c)));
        }
        long res = acc.reduceLanes(VectorOperators.ADD);
        bh.consume(res);
    }
}
