/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util.random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Param;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.concurrent.TimeUnit;

/**
 * Tests java.util.random.RandomGenerator's implementations of nextExponential and nextGaussian
 */
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class RandomGeneratorExponentialGaussian {

    RandomGenerator randomGenerator;

    @Param({"L64X128MixRandom", "L64X1024MixRandom"})
    String randomGeneratorName;

    @Param({"false","true"})
    boolean fixedSeed;

    double[] buffer;

    @Param("1024")
    int size;

    @Setup
    public void setup() {
        buffer = new double[size];
        RandomGeneratorFactory factory = RandomGeneratorFactory.of(randomGeneratorName);
        randomGenerator = fixedSeed ? factory.create(randomGeneratorName.hashCode()) : factory.create();
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.AverageTime})
    public double testNextGaussian() {
        return randomGenerator.nextGaussian();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public double[] testFillBufferWithNextGaussian() {
        for (int i = 0; i < size; i++) buffer[i] = randomGenerator.nextGaussian();
        return buffer;
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.AverageTime})
    public double testNextExponential() {
        return randomGenerator.nextExponential();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public double[] testFillBufferWithNextExponential() {
        for (int i = 0; i < size; i++) buffer[i] = randomGenerator.nextExponential();
        return buffer;
    }

}
