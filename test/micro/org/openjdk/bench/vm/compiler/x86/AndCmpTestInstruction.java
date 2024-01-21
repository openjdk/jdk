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
package org.openjdk.bench.vm.compiler.x86;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 2)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class AndCmpTestInstruction {
    long[] valuesLong;
    long valueMaskLong;
    int[] valuesInt;
    int valueMaskInt;

    @Setup
    public void setup() {
        Random random = new Random(42);
        valuesLong = new long[128];
        for (int i = 0; i < valuesLong.length; i++) {
            valuesLong[i] = random.nextLong();
        }
        valueMaskLong = 1000;

        valuesInt = new int[128];
        for (int i = 0; i < valuesInt.length; i++) {
            valuesInt[i] = random.nextInt();
        }
        valueMaskInt = 1000;
    }

    @Benchmark
    public void benchmarkStaticLargeAndCmpEqualsLong(Blackhole bh) {
        for (int i = 0; i < valuesLong.length; i++) {
            long value = valuesLong[i];
            long withAnd = value & 300000000000000L;
            bh.consume(withAnd == 0);
        }
    }

    @Benchmark
    public void benchmarkStaticSmallAndCmpEqualsLong(Blackhole bh) {
        for (int i = 0; i < valuesLong.length; i++) {
            long value = valuesLong[i];
            long withAnd = value & 300L;
            bh.consume(withAnd == 0);
        }
    }

    @Benchmark
    public void benchmarkOpaqueAndCmpEqualsLong(Blackhole bh) {
        for (int i = 0; i < valuesLong.length; i++) {
            long value = valuesLong[i];
            long withAnd = value & valueMaskLong;
            bh.consume(withAnd == 0);
        }
    }

    @Benchmark
    public void benchmarkStaticAndCmpEqualsInt(Blackhole bh) {
        for (int i = 0; i < valuesInt.length; i++) {
            long value = valuesInt[i];
            long withAnd = value & 300;
            bh.consume(withAnd == 0);
        }
    }

    @Benchmark
    public void benchmarkOpaqueAndCmpEqualsInt(Blackhole bh) {
        for (int i = 0; i < valuesInt.length; i++) {
            long value = valuesInt[i];
            long withAnd = value & valueMaskInt;
            bh.consume(withAnd == 0);
        }
    }
}
