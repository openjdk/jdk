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
    Object[] resObject;
    Object ro1;
    Object ro2;
    Object[] resClass;
    Class rc1;
    Class rc2;

    @Setup
    public void setup() {
        var random = RandomGenerator.getDefault();
        l1 = new long[INVOCATIONS];
        l2 = new long[INVOCATIONS];
        res = new int[INVOCATIONS];
        resLong = new long[INVOCATIONS];
        resObject = new Object[INVOCATIONS];
        ro1 = new Object();
        ro2 = new Object();
        resClass = new Class[INVOCATIONS];
        rc1 = Float.class;
        rc2 = Double.class;
        for (int i = 0; i < INVOCATIONS; i++) {
            l1[i] = random.nextLong(INVOCATIONS);
            l2[i] = random.nextLong(INVOCATIONS);
        }
    }

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

    // --------- result: long ---------

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
}
