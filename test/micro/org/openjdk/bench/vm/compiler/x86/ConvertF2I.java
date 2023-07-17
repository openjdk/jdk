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
package org.openjdk.bench.vm.compiler.x86;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:-UseSuperWord"})
public class ConvertF2I {
    static final int LENGTH = 1000;
    static final int[] INT_ARRAY = new int[LENGTH];
    static final long[] LONG_ARRAY = new long[LENGTH];
    static final float[] FLOAT_ARRAY = new float[LENGTH];
    static final double[] DOUBLE_ARRAY = new double[LENGTH];
    float f;
    double d;

    @Benchmark
    public int f2iSingle() {
        return (int)f;
    }

    @Benchmark
    public long f2lSingle() {
        return (long)f;
    }

    @Benchmark
    public int d2iSingle() {
        return (int)d;
    }

    @Benchmark
    public long d2lSingle() {
        return (long)d;
    }

    @Benchmark
    public void f2iArray() {
        for (int i = 0; i < LENGTH; i++) {
            INT_ARRAY[i] = (int)FLOAT_ARRAY[i];
        }
    }

    @Benchmark
    public void f2lArray() {
        for (int i = 0; i < LENGTH; i++) {
            LONG_ARRAY[i] = (long)FLOAT_ARRAY[i];
        }
    }

    @Benchmark
    public void d2iArray() {
        for (int i = 0; i < LENGTH; i++) {
            INT_ARRAY[i] = (int)DOUBLE_ARRAY[i];
        }
    }

    @Benchmark
    public void d2lArray() {
        for (int i = 0; i < LENGTH; i++) {
            LONG_ARRAY[i] = (long)DOUBLE_ARRAY[i];
        }
    }
}
