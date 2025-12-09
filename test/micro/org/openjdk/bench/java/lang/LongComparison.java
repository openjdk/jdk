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

import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class LongComparison {
    static final int INVOCATIONS = 1024;

    long[] l1;
    long[] l2;
    int[] res;
    long[] resLong;
    float[] resFloat;
    double[] resDouble;

    @Setup
    public void setup() {
        var random = RandomGenerator.getDefault();
        l1 = new long[INVOCATIONS];
        l2 = new long[INVOCATIONS];
        res = new int[INVOCATIONS];
        resLong = new long[INVOCATIONS];
        resFloat = new float[INVOCATIONS];
        resDouble = new double[INVOCATIONS];
        for (int i = 0; i < INVOCATIONS; i++) {
            l1[i] = random.nextLong(INVOCATIONS);
            l2[i] = random.nextLong(INVOCATIONS);
        }
    }

    // --------- result: int ---------
    //      Signed comparison

    @Benchmark
    public void equalLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (l1[i] == l2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void notEqualLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (l1[i] != l2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void lessLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (l1[i] < l2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void lessEqualLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (l1[i] <= l2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (l1[i] > l2[i]) ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterEqualLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (l1[i] >= l2[i]) ? 1 : 2;
        }
    }

    //      Unsigned comparison

    @Benchmark
    public void equalLongUnsigned() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Long.compareUnsigned(l1[i], l2[i]) == 0 ? 1 : 2;
        }
    }

    @Benchmark
    public void notEqualLongUnsigned() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Long.compareUnsigned(l1[i], l2[i]) != 0 ? 1 : 2;
        }
    }

    @Benchmark
    public void lessLongUnsigned() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Long.compareUnsigned(l1[i], l2[i]) < 0 ? 1 : 2;
        }
    }

    @Benchmark
    public void lessEqualLongUnsigned() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Long.compareUnsigned(l1[i], l2[i]) <= 0 ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterLongUnsigned() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Long.compareUnsigned(l1[i], l2[i]) > 0 ? 1 : 2;
        }
    }

    @Benchmark
    public void greaterEqualLongUnsigned() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Long.compareUnsigned(l1[i], l2[i]) >= 0 ? 1 : 2;
        }
    }

    // --------- result: long ---------
    //      Signed comparison

    @Benchmark
    public void equalLongResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (l1[i] == l2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void notEqualLongResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (l1[i] != l2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessLongResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (l1[i] < l2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessEqualLongResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (l1[i] <= l2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterLongResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (l1[i] > l2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterEqualLongResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = (l1[i] >= l2[i]) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    //      Unsigned comparison

    @Benchmark
    public void equalLongUnsignedResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = Long.compareUnsigned(l1[i], l2[i]) == 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void notEqualLongUnsignedResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = Long.compareUnsigned(l1[i], l2[i]) != 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessLongUnsignedResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = Long.compareUnsigned(l1[i], l2[i]) < 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void lessEqualLongUnsignedResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = Long.compareUnsigned(l1[i], l2[i]) <= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterLongUnsignedResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = Long.compareUnsigned(l1[i], l2[i]) > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    @Benchmark
    public void greaterEqualLongUnsignedResLong() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resLong[i] = Long.compareUnsigned(l1[i], l2[i]) >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    // --------- result: float ---------
    //      Signed comparison

    @Benchmark
    public void equalLongResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (l1[i] == l2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void notEqualLongResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (l1[i] != l2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessLongResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (l1[i] < l2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessEqualLongResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (l1[i] <= l2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterLongResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (l1[i] > l2[i]) ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterEqualLongResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = (l1[i] >= l2[i]) ? 0.1f : 0.2f;
        }
    }

    //      Unsigned comparison

    @Benchmark
    public void equalLongUnsignedResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = Long.compareUnsigned(l1[i], l2[i]) == 0 ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void notEqualLongUnsignedResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = Long.compareUnsigned(l1[i], l2[i]) != 0 ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessLongUnsignedResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = Long.compareUnsigned(l1[i], l2[i]) < 0 ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void lessEqualLongUnsignedResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = Long.compareUnsigned(l1[i], l2[i]) <= 0 ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterLongUnsignedResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = Long.compareUnsigned(l1[i], l2[i]) > 0 ? 0.1f : 0.2f;
        }
    }

    @Benchmark
    public void greaterEqualLongUnsignedResFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resFloat[i] = Long.compareUnsigned(l1[i], l2[i]) >= 0 ? 0.1f : 0.2f;
        }
    }

    // --------- result: double ---------
    //      Signed comparison

    @Benchmark
    public void equalLongResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (l1[i] == l2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void notEqualLongResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (l1[i] != l2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void lessLongResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (l1[i] < l2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void lessEqualLongResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (l1[i] <= l2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void greaterLongResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (l1[i] > l2[i]) ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void greaterEqualLongResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = (l1[i] >= l2[i]) ? 0.1 : 0.2;
        }
    }

    //      Unsigned comparison

    @Benchmark
    public void equalLongUnsignedResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = Long.compareUnsigned(l1[i], l2[i]) == 0 ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void notEqualLongUnsignedResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = Long.compareUnsigned(l1[i], l2[i]) != 0 ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void lessLongUnsignedResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = Long.compareUnsigned(l1[i], l2[i]) < 0 ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void lessEqualLongUnsignedResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = Long.compareUnsigned(l1[i], l2[i]) <= 0 ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void greaterLongUnsignedResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = Long.compareUnsigned(l1[i], l2[i]) > 0 ? 0.1 : 0.2;
        }
    }

    @Benchmark
    public void greaterEqualLongUnsignedResDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            resDouble[i] = Long.compareUnsigned(l1[i], l2[i]) >= 0 ? 0.1 : 0.2;
        }
    }
}
