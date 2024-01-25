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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 4, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class PhiDuplicatedConversion {
    public static final int SIZE = 300;

    // Ints

    @Benchmark
    public void testInt2Float(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(i2f(state.bools[i], state.ints[i], state.ints[SIZE - 1 - i]));
        }
    }

    @Benchmark
    public void testInt2Double(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(i2d(state.bools[i], state.ints[i], state.ints[SIZE - 1 - i]));
        }
    }

    @Benchmark
    public void testInt2Long(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(i2l(state.bools[i], state.ints[i], state.ints[SIZE - 1 - i]));
        }
    }

    // Floats

    @Benchmark
    public void testFloat2Int(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(f2i(state.bools[i], state.floats[i], state.floats[SIZE - 1 - i]));
        }
    }

    @Benchmark
    public void testFloat2Double(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(f2d(state.bools[i], state.floats[i], state.floats[SIZE - 1 - i]));
        }
    }

    @Benchmark
    public void testFloat2Long(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(f2l(state.bools[i], state.floats[i], state.floats[SIZE - 1 - i]));
        }
    }

    // Doubles

    @Benchmark
    public void testDouble2Int(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(d2i(state.bools[i], state.doubles[i], state.doubles[SIZE - 1 - i]));
        }
    }

    @Benchmark
    public void testDouble2Float(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(d2f(state.bools[i], state.doubles[i], state.doubles[SIZE - 1 - i]));
        }
    }

    @Benchmark
    public void testDouble2Long(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(d2l(state.bools[i], state.doubles[i], state.doubles[SIZE - 1 - i]));
        }
    }

    // Longs

    @Benchmark
    public void testLong2Float(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(l2f(state.bools[i], state.longs[i], state.longs[SIZE - 1 - i]));
        }
    }

    @Benchmark
    public void testLong2Double(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(l2d(state.bools[i], state.longs[i], state.longs[SIZE - 1 - i]));
        }
    }

    @Benchmark
    public void testLong2Int(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < SIZE; i++) {
            blackhole.consume(l2i(state.bools[i], state.longs[i], state.longs[SIZE - 1 - i]));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static float i2f(boolean c, int a, int b) {
        return c ? a : b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static double i2d(boolean c, int a, int b) {
        return c ? a : b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static long i2l(boolean c, int a, int b) {
        return c ? a : b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static int f2i(boolean c, float a, float b) {
        return c ? (int)a : (int)b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static double f2d(boolean c, float a, float b) {
        return c ? (double)a : (double)b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static long f2l(boolean c, float a, float b) {
        return c ? (long)a : (long)b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static int d2i(boolean c, double a, double b) {
        return c ? (int)a : (int)b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static float d2f(boolean c, double a, double b) {
        return c ? (float)a : (float)b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static long d2l(boolean c, double a, double b) {
        return c ? (long)a : (long)b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static float l2f(boolean c, long a, long b) {
        return c ? a : b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static double l2d(boolean c, long a, long b) {
        return c ? a : b;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static int l2i(boolean c, long a, long b) {
        return c ? (int)a : (int)b;
    }

    @State(Scope.Benchmark)
    public static class BenchState {
        public boolean[] bools;
        public int[] ints;
        public long[] longs;
        public float[] floats;
        public double[] doubles;
        public BenchState() {

        }

        @Setup(Level.Iteration)
        public void setup() {
            Random random = new Random(1000);
            bools = new boolean[SIZE];
            ints = new int[SIZE];
            longs = new long[SIZE];
            floats = new float[SIZE];
            doubles = new double[SIZE];

            for (int i = 0; i < SIZE; i++) {
                bools[i] = random.nextBoolean();
                ints[i] = random.nextInt(100);
                longs[i] = random.nextLong(100);
                floats[i] = random.nextFloat(100);
                doubles[i] = random.nextDouble(100);
            }
        }
    }
}