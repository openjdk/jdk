/*
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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
public class IntegerComparison {
    static final int INVOCATIONS = 1024;

    int[] i1;
    int[] i2;
    int[] res;
    long[] resLong;
    float[] resFloat;
    double[] resDouble;

    @Setup
    public void setup() {
        var random = RandomGenerator.getDefault();
        i1 = new int[INVOCATIONS];
        i2 = new int[INVOCATIONS];
        res = new int[INVOCATIONS];
        resLong = new long[INVOCATIONS];
        resFloat = new float[INVOCATIONS];
        resDouble = new double[INVOCATIONS];
        for (int i = 0; i < INVOCATIONS; i++) {
            i1[i] = random.nextInt(INVOCATIONS);
            i2[i] = random.nextInt(INVOCATIONS);
        }
    }

    @Benchmark
    public void equalInteger() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (i1[i] == i2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void notEqualInteger() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (i1[i] != i2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void lessInteger() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (i1[i] < i2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void lessEqualInteger() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (i1[i] <= i2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterInteger() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (i1[i] > i2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterEqualInteger() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (i1[i] >= i2[i]) ? 1 : 2;
        }
    }

    // --------- result: long ---------

    @Benchmark
    public void equalIntegerResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (i1[i] == i2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void notEqualIntegerResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (i1[i] != i2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessIntegerResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (i1[i] < i2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessEqualIntegerResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (i1[i] <= i2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterIntegerResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (i1[i] > i2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterEqualIntegerResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (i1[i] >= i2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    // --------- result: float ---------

    @Benchmark
    public void equalIntegerResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (i1[i] == i2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void notEqualIntegerResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (i1[i] != i2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessIntegerResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (i1[i] < i2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessEqualIntegerResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (i1[i] <= i2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterIntegerResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (i1[i] > i2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterEqualIntegerResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (i1[i] >= i2[i]) ? 0.1f : 0.2f;
        }
    }

    // --------- result: double ---------

    @Benchmark
    public void equalIntegerResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (i1[i] == i2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void notEqualIntegerResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (i1[i] != i2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void lessIntegerResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (i1[i] < i2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void lessEqualIntegerResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (i1[i] <= i2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void greaterIntegerResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (i1[i] > i2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void greaterEqualIntegerResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (i1[i] >= i2[i]) ? 0.1 : 0.2;
        }
    }
}
