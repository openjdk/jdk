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
public class TestRemovalPeephole {
    long[] valuesLong1;
    long[] valuesLong2;
    int[] valuesInt1;
    int[] valuesInt2;
    long valueLong1;
    long valueLong2;
    int valueInt1;
    int valueInt2;

    @Setup
    public void setup() {
        Random random = new Random(42);
        valuesLong1 = new long[128];
        valuesLong2 = new long[128];
        for (int i = 0; i < valuesLong1.length; i++) {
            valuesLong1[i] = random.nextLong();
        }
        for (int i = 0; i < valuesLong2.length; i++) {
            valuesLong2[i] = random.nextLong();
        }

        valuesInt1 = new int[128];
        valuesInt2 = new int[128];
        for (int i = 0; i < valuesInt1.length; i++) {
            valuesInt1[i] = random.nextInt();
        }
        for (int i = 0; i < valuesInt2.length; i++) {
            valuesInt2[i] = random.nextInt();
        }
        valueLong1 = random.nextLong();
        valueLong2 = random.nextLong();
        valueInt1 = random.nextInt();
        valueInt2 = random.nextInt();
    }

    @Benchmark
    public void benchmarkAndTestFusableInt(Blackhole bh) {
        for (int i = 0; i < valuesInt1.length; i++) {
            int value1 = valuesInt1[i];
            int value2 = valuesInt2[i];
            int withAnd1 = value1 & 0xF;
            int withAnd2 = value2 & 0xF;

            bh.consume(withAnd1 > 0x0 && withAnd2 > 0x0 && withAnd1 < 0xF && withAnd2 < 0xF);
        }
    }

    @Benchmark
    public void benchmarkAndTestFusableLong(Blackhole bh) {
        for (int i = 0; i < valuesLong1.length; i++) {
            long value1 = valuesLong1[i];
            long value2 = valuesLong2[i];
            long withAnd1 = value1 & 0xFFFFFFFFFFL;
            long withAnd2 = value2 & 0xFFFFFFFFFFL;

            bh.consume(withAnd1 > 0x0L && withAnd2 > 0x0L && withAnd1 < 0xFFFFFFFFFFL && withAnd2 < 0xFFFFFFFFFFL);
        }
    }

    @Benchmark
    public void benchmarkOrTestFusableInt(Blackhole bh) {
        for (int i = 0; i < valuesInt1.length; i++) {
            int value1 = valuesInt1[i];
            int value2 = valuesInt2[i];
            int withAnd1 = value1 | 0xF;
            int withAnd2 = value2 | 0xF;

            bh.consume(withAnd1 > 0x0 && withAnd2 > 0x0 && withAnd1 < 0xF && withAnd2 < 0xF);
        }
    }

    @Benchmark
    public void benchmarkOrTestFusableLong(Blackhole bh) {
        for (int i = 0; i < valuesLong1.length; i++) {
            long value1 = valuesLong1[i];
            long value2 = valuesLong2[i];
            long withAnd1 = value1 | 0xFFFFFFFFFFL;
            long withAnd2 = value2 | 0xFFFFFFFFFFL;

            bh.consume(withAnd1 > 0x0L && withAnd2 > 0x0L && withAnd1 < 0xFFFFFFFFFFL && withAnd2 < 0xFFFFFFFFFFL);
        }
    }

    @Benchmark
    public void benchmarkXorTestFusableInt(Blackhole bh) {
        for (int i = 0; i < valuesInt1.length; i++) {
            int value1 = valuesInt1[i];
            int value2 = valuesInt2[i];
            int withAnd1 = value1 ^ 0xF;
            int withAnd2 = value2 ^ 0xF;

            bh.consume(withAnd1 > 0x0 && withAnd2 > 0x0 && withAnd1 < 0xF && withAnd2 < 0xF);
        }
    }

    @Benchmark
    public void benchmarkXorTestFusableLong(Blackhole bh) {
        for (int i = 0; i < valuesLong1.length; i++) {
            long value1 = valuesLong1[i];
            long value2 = valuesLong2[i];
            long withAnd1 = value1 ^ 0xFFFFFFFFFFL;
            long withAnd2 = value2 ^ 0xFFFFFFFFFFL;

            bh.consume(withAnd1 > 0x0L && withAnd2 > 0x0L && withAnd1 < 0xFFFFFFFFFFL && withAnd2 < 0xFFFFFFFFFFL);
        }
    }


    @Benchmark
    public void benchmarkAndTestFusableIntSingle(Blackhole bh) {
        int withAnd1 = valueInt1 & 0xF;
        int withAnd2 = valueInt2 & 0xF;

        bh.consume(withAnd1 > 0x0 && withAnd2 > 0x0 && withAnd1 < 0xF && withAnd2 < 0xF);
    }

    @Benchmark
    public void benchmarkAndTestFusableLongSingle(Blackhole bh) {
        long withAnd1 = valueLong1 & 0xFFFFFFFFFFL;
        long withAnd2 = valueLong2 & 0xFFFFFFFFFFL;

        bh.consume(withAnd1 > 0x0L && withAnd2 > 0x0L && withAnd1 < 0xFFFFFFFFFFL && withAnd2 < 0xFFFFFFFFFFL);
    }

    @Benchmark
    public void benchmarkOrTestFusableIntSingle(Blackhole bh) {
        int withAnd1 = valueInt1 | 0xF;
        int withAnd2 = valueInt2 | 0xF;

        bh.consume(withAnd1 > 0x0 && withAnd2 > 0x0 && withAnd1 < 0xF && withAnd2 < 0xF);
    }

    @Benchmark
    public void benchmarkOrTestFusableLongSingle(Blackhole bh) {
        long withAnd1 = valueLong1 | 0xFFFFFFFFFFL;
        long withAnd2 = valueLong2 | 0xFFFFFFFFFFL;

        bh.consume(withAnd1 > 0x0L && withAnd2 > 0x0L && withAnd1 < 0xFFFFFFFFFFL && withAnd2 < 0xFFFFFFFFFFL);
    }

    @Benchmark
    public void benchmarkXorTestFusableIntSingle(Blackhole bh) {
        int withAnd1 = valueInt1 ^ 0xF;
        int withAnd2 = valueInt2 ^ 0xF;

        bh.consume(withAnd1 > 0x0 && withAnd2 > 0x0 && withAnd1 < 0xF && withAnd2 < 0xF);
    }

    @Benchmark
    public void benchmarkXorTestFusableLongSingle(Blackhole bh) {
        long withAnd1 = valueLong1 ^ 0xFFFFFFFFFFL;
        long withAnd2 = valueLong2 ^ 0xFFFFFFFFFFL;

        bh.consume(withAnd1 > 0x0L && withAnd2 > 0x0L && withAnd1 < 0xFFFFFFFFFFL && withAnd2 < 0xFFFFFFFFFFL);
    }
}
