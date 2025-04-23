/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class FPComparison {
    static final int INVOCATIONS = 10_000;

    static final float[] f1 = new float[INVOCATIONS];
    static final double[] d1 = new double[INVOCATIONS];
    static final float[] f2 = new float[INVOCATIONS];;
    static final double[] d2 = new double[INVOCATIONS];;
    static final int[] res = new int[INVOCATIONS];;
    static final long[] resLong = new long[INVOCATIONS];;
    static final float[] resFloat = new float[INVOCATIONS];;
    static final Object[] resObject = new Object[INVOCATIONS];;
    static final Object ro1 = new Object();;
    static final Object ro2 = new Object();;
    static final Class[] resClass = new Class[INVOCATIONS];;
    static final Class rc1 = Float.class;;
    static final Class rc2 = Double.class;;

    @Setup
    public void setup() {
        var random = RandomGenerator.getDefault();
        for (int i = 0; i < INVOCATIONS; i++) {
            int type = random.nextInt(5);
            if (type == 1) {
                f1[i] = random.nextFloat();
                d1[i] = random.nextDouble();
                f2[i] = random.nextFloat();
                d2[i] = random.nextDouble();
            } else if (type == 2) {
                f1[i] = Float.POSITIVE_INFINITY;
                d1[i] = Double.POSITIVE_INFINITY;
                f2[i] = Float.POSITIVE_INFINITY;
                d2[i] = Double.POSITIVE_INFINITY;
            } else if (type == 3) {
                f1[i] = Float.NEGATIVE_INFINITY;
                d1[i] = Double.NEGATIVE_INFINITY;
                f2[i] = Float.NEGATIVE_INFINITY;
                d2[i] = Double.NEGATIVE_INFINITY;
            } else if (type >= 4) {
                f1[i] = Float.NaN;
                d1[i] = Double.NaN;
                f2[i] = Float.NaN;
                d2[i] = Double.NaN;
            }
        }
    }

    @Benchmark
    public void isNanFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isNaN(f1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void isNanDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isNaN(d1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void isInfiniteFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isInfinite(f1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void isInfiniteDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isInfinite(d1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void isFiniteFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isFinite(f1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void isFiniteDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isFinite(d1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void equalFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] == f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void equalDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] == d2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void lessFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] < f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void lessDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] < d2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void lessEqualFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] <= f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void lessEqualDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] <= d2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] > f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] > d2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterEqualFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] >= f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterEqualDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] >= d2[i]) ? 1 : 2;
        }
    }

    // --------- result: long ---------

    @Benchmark
    public void equalFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] == f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void equalDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] == d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] < f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] < d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessEqualFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] <= f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessEqualDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] <= d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] > f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] > d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterEqualFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] >= f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterEqualDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] >= d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    // --------- result: float ---------
    private static void init(float[] a) {
        RandomGenerator random = RandomGenerator.getDefault();
        for (int i = 0; i < SIZE; i++) {
            a[i] = switch(random.nextInt() % 20) {
                case 0  -> Float.NaN;
                case 1  -> 0;
                case 2  -> 1;
                case 3  -> Float.POSITIVE_INFINITY;
                case 4  -> Float.NEGATIVE_INFINITY;
                case 5  -> Float.MAX_VALUE;
                case 6  -> Float.MIN_VALUE;
                case 7, 8, 9 -> random.nextFloat();
                default -> Float.intBitsToFloat(random.nextInt());
            };
        }
    }

    @Benchmark
    public void equalFloatResFloatLocal() {
        final int size = 1024;
        float[] f1 = new float[size];
        float[] f2 = new float[size];
        float[] f3 = new float[size];
        init(f1);
        init(f2);
        init(f3);
        for (int i = 0; i < size; i++) {
            f3[i] = (f1[i] == f2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void equalFloatResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (f1[i] == f2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void equalDoubleResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (d1[i] == d2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessFloatResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (f1[i] < f2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessDoubleResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (d1[i] < d2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessEqualFloatResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (f1[i] <= f2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessEqualDoubleResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (d1[i] <= d2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterFloatResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (f1[i] > f2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterDoubleResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (d1[i] > d2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterEqualFloatResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (f1[i] >= f2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterEqualDoubleResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (d1[i] >= d2[i]) ? 0.1f : 0.2f;
        }
    }




    @Benchmark
    public void equalFloatResFloatState(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < state.f1.length; i++) {
            state.f3[i] = (state.f1[i] == state.f2[i]) ? 0.1f : 0.2f;
        }
        blackhole.consume(state.f3);
    }

    @Benchmark
    public void maxFloatResFloatState(Blackhole blackhole, BenchState state) {
        for (int i = 0; i < state.f1.length; i++) {
            state.f3[i] = (state.f1[i] > state.f2[i]) ? state.f1[i] : state.f2[i];
        }
        blackhole.consume(state.f3);
    }

    private static final int SIZE = 10_000;

    @State(Scope.Benchmark)
    public static class BenchState {
        private final float[] f1 = new float[SIZE];
        private final float[] f2 = new float[SIZE];
        private final float[] f3 = new float[SIZE];

        public BenchState() { }

        private RandomGenerator random = RandomGenerator.getDefault();

        @Setup
        public void setup() {
            init(f1);
            init(f2);
            init(f3);

            for (int i = 0; i < SIZE; i++) {
                if (i % 3 == 0) {
                    f2[i] = f1[i];
                }
            }
        }

        private void init(float[] a) {
            for (int i = 0; i < SIZE; i++) {
                a[i] = switch(random.nextInt() % 20) {
                    case 0  -> Float.NaN;
                    case 1  -> 0;
                    case 2  -> 1;
                    case 3  -> Float.POSITIVE_INFINITY;
                    case 4  -> Float.NEGATIVE_INFINITY;
                    case 5  -> Float.MAX_VALUE;
                    case 6  -> Float.MIN_VALUE;
                    case 7, 8, 9 -> random.nextFloat();
                    default -> Float.intBitsToFloat(random.nextInt());
                };
            }
        }
    }
}
