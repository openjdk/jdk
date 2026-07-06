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
package org.openjdk.bench.vm.compiler;

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

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks patterns that may fuse low/high 64-bit multiply operations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class MultiplyHighLowFusion {

    @Param("1024")
    private int arraySize;

    private long[] lhs;
    private long[] rhs;

    @Setup
    public void setup() {
        Random random = new Random(0x5EED);
        lhs = new long[arraySize];
        rhs = new long[arraySize];
        for (int i = 0; i < arraySize; i++) {
            lhs[i] = random.nextLong();
            rhs[i] = random.nextLong();
        }
    }

    @Benchmark
    public long signedLowOnly() {
        long sum = 0;
        for (int i = 0; i < arraySize; i++) {
            sum += lhs[i] * rhs[i];
        }
        return sum;
    }

    @Benchmark
    public long signedHighOnly() {
        long sum = 0;
        for (int i = 0; i < arraySize; i++) {
            sum += Math.multiplyHigh(lhs[i], rhs[i]);
        }
        return sum;
    }

    @Benchmark
    public long signedLowPlusHigh() {
        long sum = 0;
        for (int i = 0; i < arraySize; i++) {
            long a = lhs[i];
            long b = rhs[i];
            sum += (a * b) + Math.multiplyHigh(a, b);
        }
        return sum;
    }

    @Benchmark
    public long unsignedHighOnly() {
        long sum = 0;
        for (int i = 0; i < arraySize; i++) {
            sum += Math.unsignedMultiplyHigh(lhs[i], rhs[i]);
        }
        return sum;
    }

    @Benchmark
    public long unsignedLowPlusHigh() {
        long sum = 0;
        for (int i = 0; i < arraySize; i++) {
            long a = lhs[i];
            long b = rhs[i];
            sum += (a * b) + Math.unsignedMultiplyHigh(a, b);
        }
        return sum;
    }
}
