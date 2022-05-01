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

import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)
public class FPComparison {
    static final int INVOCATIONS = 1024;

    float[] f1;
    double[] d1;
    float[] f2;
    double[] d2;
    int[] res;

    @Setup
    public void setup() {
        var random = RandomGenerator.getDefault();
        f1 = new float[INVOCATIONS];
        d1 = new double[INVOCATIONS];
        f2 = new float[INVOCATIONS];
        d2 = new double[INVOCATIONS];
        res = new int[INVOCATIONS];
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
            res[i] = Float.isNaN(f1[i]) ? 1 : 0;
        }
    }

    @Benchmark
    public void isNanDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isNaN(d1[i]) ? 1 : 0;
        }
    }

    @Benchmark
    public void isInfiniteFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isInfinite(f1[i]) ? 1 : 0;
        }
    }

    @Benchmark
    public void isInfiniteDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isInfinite(d1[i]) ? 1 : 0;
        }
    }

    @Benchmark
    public void isFiniteFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Float.isFinite(f1[i]) ? 1 : 0;
        }
    }

    @Benchmark
    public void isFiniteDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = Double.isFinite(d1[i]) ? 1 : 0;
        }
    }

    @Benchmark
    public void equalFloat() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (f1[i] == f2[i]) ? 1 : 0;
        }
    }

    @Benchmark
    public void equalDouble() {
        for (int i = 0; i < INVOCATIONS; i++) {
            res[i] = (d1[i] == d2[i]) ? 1 : 0;
        }
    }
}
