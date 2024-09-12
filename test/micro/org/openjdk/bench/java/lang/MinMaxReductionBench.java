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
import java.util.stream.IntStream;
import java.util.stream.LongStream;

@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 4, time = 5)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
public class MinMaxReductionBench {

    @Param({"100", "1000", "10000"})
    int size;

    /**
     * Probability of one of the min/max branches being taken.
     * For max, this value represents the percentage of branches in which
     * the value will be bigger or equal than the current max.
     * For min, this value represents the percentage of branches in which
     * the value will be smaller or equal than the current min.
     */
    @Param({"50", "80", "100"})
    int probability;

    public int[] maxIntA;
    public int[] minIntA;
    public int[] maxIntB;
    public int[] minIntB;
    public int[] maxIntC;
    public int[] minIntC;
    public long[] maxLongA;
    public long[] minLongA;
    public long[] maxLongB;
    public long[] minLongB;
    public long[] maxLongC;
    public long[] minLongC;

    @Setup
    public void setup() {
        maxIntA = distributeIntRandomIncrement(size, probability);
        minIntA = negate(distributeIntRandomIncrement(size, probability));
        maxIntB = distributeIntRandomIncrement(size, probability);
        minIntB = negate(distributeIntRandomIncrement(size, probability));
        maxIntC = distributeIntRandomIncrement(size, probability);
        minIntC = negate(distributeIntRandomIncrement(size, probability));
        maxLongA = distributeLongRandomIncrement(size, probability);
        minLongA = negate(distributeLongRandomIncrement(size, probability));
        maxLongB = distributeLongRandomIncrement(size, probability);
        minLongB = negate(distributeLongRandomIncrement(size, probability));
        maxLongC = distributeLongRandomIncrement(size, probability);
        minLongC = negate(distributeLongRandomIncrement(size, probability));
    }

    static long[] negate(long[] nums)
    {
        return LongStream.of(nums).map(l -> -l).toArray();
    }

    static int[] negate(int[] nums)
    {
        return IntStream.of(nums).map(i -> -i).toArray();
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
            final int v = 11 * minIntA[i];
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long multiIntMin() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = (minIntA[i] * minIntB[i]) + (minIntA[i] * minIntC[i]) + (minIntB[i] * minIntC[i]);
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long singleIntMax() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = 11 * maxIntA[i];
            result = Math.max(result, v);
        }
        return result;
    }

    @Benchmark
    public long multiIntMax() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = (maxIntA[i] * maxIntB[i]) + (maxIntA[i] * maxIntC[i]) + (maxIntB[i] * maxIntC[i]);
            result = Math.max(result, v);
        }
        return result;
    }

    @Benchmark
    public long singleLongMin() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = 11 * minLongA[i];
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long multiLongMin() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = (minLongA[i] * minLongB[i]) + (minLongA[i] * minLongC[i]) + (minLongB[i] * minLongC[i]);
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long singleLongMax() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = 11 * maxLongA[i];
            result = Math.max(result, v);
        }
        return result;
    }

    @Benchmark
    public long multiLongMax() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = (maxLongA[i] * maxLongB[i]) + (maxLongA[i] * maxLongC[i]) + (maxLongB[i] * maxLongC[i]);
            result = Math.max(result, v);
        }
        return result;
    }
}
