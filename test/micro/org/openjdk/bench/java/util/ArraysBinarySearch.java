/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package  org.openjdk.bench.java.util;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.Random;

/**
 * Performance test of Arrays.binarySearch() methods
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time=5)
@Measurement(iterations = 3, time=3)
public class ArraysBinarySearch {

    @Param({"10","25","50","75","100", "1000", "10000", "100000", "1000000"})
    private int size;

    private int[] ints;
    private long[] longs;
    private float[] floats;
    private double[] doubles;

    int intNeedle;
    long longNeedle;
    long floatNeedle;
    long doubleNeedle;

    public void initialize() {
        Random rnd = new Random(42);

        ints = new int[size];
        longs = new long[size];
        floats = new float[size];
        doubles = new double[size];

        int[] intSpecialCases = {Integer.MIN_VALUE, Integer.MAX_VALUE};
        long[] longSpecialCases = {Long.MIN_VALUE, Long.MAX_VALUE};
        float[] floatSpecialCases = {+0.0f, -0.0f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN};
        double[] doubleSpecialCases = {+0.0, -0.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};

        for (int i = 0; i < size; i++) {
            ints[i] = rnd.nextInt();
            longs[i] = rnd.nextLong();
            if (i % 10 != 0) {
                ints[i] = rnd.nextInt();
                longs[i] = rnd.nextLong();
                floats[i] = rnd.nextFloat();
                doubles[i] = rnd.nextDouble();
            } else {
                ints[i] = intSpecialCases[rnd.nextInt(intSpecialCases.length)];
                longs[i] = longSpecialCases[rnd.nextInt(longSpecialCases.length)];
                floats[i] = floatSpecialCases[rnd.nextInt(floatSpecialCases.length)];
                doubles[i] = doubleSpecialCases[rnd.nextInt(doubleSpecialCases.length)];
            }
        }
        int needleIndex = rnd.nextInt(size);
        intNeedle = ints[needleIndex];
        longNeedle = longs[needleIndex];
        floatNeedle = longs[needleIndex];
        doubleNeedle = longs[needleIndex];
    }

    @Setup
    public void setup() {
        initialize();
    }

    @Benchmark
    public int intBinarySearch() {
        return Arrays.binarySearch(ints, intNeedle);
    }

    @Benchmark
    public int longBinarySearch() {
        return Arrays.binarySearch(longs, longNeedle);
    }

    @Benchmark
    public int floatBinarySearch() {
        return Arrays.binarySearch(floats, floatNeedle);
    }

    @Benchmark
    public int doubleBinarySearch() {
        return Arrays.binarySearch(doubles, doubleNeedle);
    }

}
