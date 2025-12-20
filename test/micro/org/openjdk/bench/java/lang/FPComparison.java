/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
    static final int INVOCATIONS = 1024;

    float[] f1;
    double[] d1;
    float[] f2;
    double[] d2;
    int[] res;
    long[] resLong;
    Object[] resObject;
    Object ro1;
    Object ro2;
    Class[] resClass;
    Class rc1;
    Class rc2;

    @Setup
    public void setup() {
        var random = RandomGenerator.getDefault();
        f1 = new float[INVOCATIONS];
        d1 = new double[INVOCATIONS];
        f2 = new float[INVOCATIONS];
        d2 = new double[INVOCATIONS];
        res = new int[INVOCATIONS];
        resLong = new long[INVOCATIONS];
        resObject = new Object[INVOCATIONS];
        ro1 = new Object();
        ro2 = new Object();
        resClass = new Class[INVOCATIONS];
        rc1 = Float.class;
        rc2 = Double.class;
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

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    static int callI() {
        return 1;
    }

    @Benchmark
    public void cMoveIsNanFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isNaN(f1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveIsNanDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isNaN(d1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveIsInfiniteFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isInfinite(f1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveIsInfiniteDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isInfinite(d1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveIsFiniteFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isFinite(f1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveIsFiniteDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isFinite(d1[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveEqualFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] == f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveEqualDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] == d2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveLessFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] < f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveLessDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] < d2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveLessEqualFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] <= f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveLessEqualDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] <= d2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveGreaterFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] > f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveGreaterDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] > d2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveGreaterEqualFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] >= f2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void cMoveGreaterEqualDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] >= d2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void branchIsNanFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isNaN(f1[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchIsNanDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isNaN(d1[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchIsInfiniteFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isInfinite(f1[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchIsInfiniteDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isInfinite(d1[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchIsFiniteFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isFinite(f1[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchIsFiniteDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isFinite(d1[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchEqualFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] == f2[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchEqualDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] == d2[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchLessFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] < f2[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchLessDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] < d2[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchLessEqualFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] <= f2[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchLessEqualDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] <= d2[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchGreaterFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] > f2[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchGreaterDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] > d2[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchGreaterEqualFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] >= f2[i]) ? callI() : 2;
        }
    }

    @Benchmark
    public void branchGreaterEqualDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] >= d2[i]) ? callI() : 2;
        }
    }

    // --------- result: long ---------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    static long callL() {
        return Long.MAX_VALUE;
    }

    @Benchmark
    public void cMoveEqualFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] == f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void cMoveEqualDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] == d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void cMoveLessFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] < f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void cMoveLessDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] < d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void cMoveLessEqualFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] <= f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void cMoveLessEqualDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] <= d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void cMoveGreaterFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] > f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void cMoveGreaterDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] > d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void cMoveGreaterEqualFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] >= f2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void cMoveGreaterEqualDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] >= d2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchEqualFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] == f2[i]) ? callL() : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchEqualDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] == d2[i]) ? callL() : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchLessFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] < f2[i]) ? callL() : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchLessDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] < d2[i]) ? callL() : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchLessEqualFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] <= f2[i]) ? callL() : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchLessEqualDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] <= d2[i]) ? callL() : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchGreaterFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] > f2[i]) ? callL() : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchGreaterDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] > d2[i]) ? callL() : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchGreaterEqualFloatResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (f1[i] >= f2[i]) ? callL() : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void branchGreaterEqualDoubleResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (d1[i] >= d2[i]) ? callL() : Long.MIN_VALUE;
        }
    }
}
