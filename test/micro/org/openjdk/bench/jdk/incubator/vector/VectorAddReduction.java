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

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import org.openjdk.jmh.infra.Blackhole;

/**
 * The code below implements two different vector reduction strategies,
 * which are based on the integer ones shown in:
 *   test/micro/org/openjdk/bench/vm/compiler/VectorAlgorithmsImpl.java
 *
 * The first implementation is a 'naive' way of writing a reduction, where the
 * loop body consists of `reduceLanes` operations. The idea is that cross-lane
 * reductions are more expensive than lane-wise additions, so this version
 * should perform the worst.
 *
 * The second implementation is 'reduction_after_loop', where lane-wise
 * additions are performed in the loop, followed by a single cross-lane
 * reduction after the loop body. Since Vector API allows reordering
 * operations for 'ADD' cross-lane reductions, we can instead use a cheaper
 * operation within the loop to calculate the result faster.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorAddReduction {

    private static final VectorSpecies<Float> F_SPECIES  = FloatVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_PREFERRED;

    @Param({"128", "512", "2048", "4096"})
    public int COUNT;

    private float[] fa;
    private float fres;
    private double[] da;
    private double dres;

    @Param("0")
    private int seed;
    private Random r;

    @Setup
    public void init() {
        r = new Random(seed);
        fa = new float[COUNT];
        da = new double[COUNT];
        fres = 1.0f;
        dres = 1.0;
        for (int i = 0; i < COUNT; i++) {
            fa[i] = r.nextFloat();
            da[i] = r.nextDouble();
        }
    }

    @Benchmark
    public void vectorFloatAddReduce_naive() {
        for (int i = 0; i < F_SPECIES.loopBound(fa.length); i += F_SPECIES.length()) {
            var av = FloatVector.fromArray(F_SPECIES, fa, i);
            fres += av.reduceLanes(VectorOperators.ADD);
        }
    }

    @Benchmark
    public void vectorDoubleAddReduce_naive() {
        for (int i = 0; i < D_SPECIES.loopBound(da.length); i += D_SPECIES.length()) {
            var ad = DoubleVector.fromArray(D_SPECIES, da, i);
            dres += ad.reduceLanes(VectorOperators.ADD);
        }
    }

    @Benchmark
    public void vectorFloatAddReduce_reduction_after_loop() {
        FloatVector vacc = FloatVector.zero(F_SPECIES);
        for (int i = 0; i < F_SPECIES.loopBound(fa.length); i += F_SPECIES.length()) {
            var av = FloatVector.fromArray(F_SPECIES, fa, i);
            vacc = vacc.add(av);
        }
        fres = vacc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public void vectorDoubleAddReduce_reduction_after_loop() {
        DoubleVector dacc = DoubleVector.zero(D_SPECIES);
        for (int i = 0; i < D_SPECIES.loopBound(da.length); i += D_SPECIES.length()) {
            var ad = DoubleVector.fromArray(D_SPECIES, da, i);
            dacc = dacc.add(ad);
        }
        dres = dacc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void vectorFloatAddReduce_latency(Blackhole bh) {
        var v = FloatVector.fromArray(F_SPECIES, fa, 0);
        float sum = 0.0f;
        for (int i = 0; i < COUNT; i++) {
            sum = v.reduceLanes(VectorOperators.ADD);
            v = v.withLane(0, sum);
        }
        bh.consume(sum);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void vectorDoubleAddReduce_latency(Blackhole bh) {
        var v = DoubleVector.fromArray(D_SPECIES, da, 0);
        double sum = 0.0;
        for (int i = 0; i < COUNT; i++) {
            sum = v.reduceLanes(VectorOperators.ADD);
            v = v.withLane(0, sum);
        }
        bh.consume(sum);
    }
}
