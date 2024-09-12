/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
package org.openjdk.bench.java.lang;

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

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 4, time = 5)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
public class MathReductionBench {

    @Param({"100", "1000", "10000"})
    int size;

    @Param({"50", "75", "100"})
    int probability;

    public int[] aInt;
    public int[] bInt;
    public int[] cInt;
    public long[] aLong;
    public long[] bLong;
    public long[] cLong;

    @Setup
    public void setup() {
        aInt = distributeIntRandomIncrement(size, probability);
        bInt = distributeIntRandomIncrement(size, probability);
        cInt = distributeIntRandomIncrement(size, probability);
        aLong = distributeLongRandomIncrement(size, probability);
        bLong = distributeLongRandomIncrement(size, probability);
        cLong = distributeLongRandomIncrement(size, probability);
    }

    static int[] distributeIntRandomIncrement(int size, int probability) {
        final long[] longs = distributeLongRandomIncrement(size, probability);
        return Arrays.stream(longs).mapToInt(i -> (int) i).toArray();
    }

    static long[] distributeLongRandomIncrement(int size, int probability) {
        long[] result;
        int aboveCount, abovePercent;

        // Iterate until you find a set that matches the requirement probability
        do {
            long max = ThreadLocalRandom.current().nextLong(10);
            result = new long[size];
            result[0] = max;

            aboveCount = 0;
            for (int i = 1; i < result.length; i++) {
                long value;

                if (ThreadLocalRandom.current().nextLong(101) <= probability) {
                    long increment = ThreadLocalRandom.current().nextLong(10);
                    value = max + increment;
                    aboveCount++;
                } else {
                    // Decrement by at least 1
                    long decrement = ThreadLocalRandom.current().nextLong(10) + 1;
                    value = max - decrement;
                }

                result[i] = value;
                max = Math.max(max, value);
            }
            abovePercent = ((aboveCount + 1) * 100) / size;
        } while (abovePercent != probability);

        return result;
    }

    @Benchmark
    public long singleIntMin() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = 11 * aInt[i];
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long multiIntMin() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = (aInt[i] * bInt[i]) + (aInt[i] * cInt[i]) + (bInt[i] * cInt[i]);
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long singleIntMax() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = 11 * aInt[i];
            result = Math.max(result, v);
        }
        return result;
    }

    @Benchmark
    public long multiIntMax() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = (aInt[i] * bInt[i]) + (aInt[i] * cInt[i]) + (bInt[i] * cInt[i]);
            result = Math.max(result, v);
        }
        return result;
    }

    @Benchmark
    public long singleLongMin() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = 11 * aLong[i];
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long multiLongMin() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = (aLong[i] * bLong[i]) + (aLong[i] * cLong[i]) + (bLong[i] * cLong[i]);
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long singleLongMax() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = 11 * aLong[i];
            result = Math.max(result, v);
        }
        return result;
    }

    @Benchmark
    public long multiLongMax() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = (aLong[i] * bLong[i]) + (aLong[i] * cLong[i]) + (bLong[i] * cLong[i]);
            result = Math.max(result, v);
        }
        return result;
    }
}
