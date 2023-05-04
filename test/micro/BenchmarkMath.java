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
package org.openjdk.bench.java.lang;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
public class BenchmarkMath {

    @Param("0")
    public long seed;

    public int dividend;
    public int divisor;

    public long longDividend;
    public long longDivisor;

    public int int1 = 1, int2 = 2, int42 = 42, int5 = 5;
    public long long1 = 1L, long2 = 2L, long747 = 747L, long13 = 13L;
    public float float1 = 1.0f, float2 = 2.0f, floatNegative99 = -99.0f, float7 = 7.0f, eFloat = 2.718f;
    public double double1 = 1.0d, double2 = 2.0d, double81 = 81.0d, doubleNegative12 = -12.0d, double4Dot1 = 4.1d, double0Dot5 = 0.5d;

    @Setup
    public void setupValues() {
        Random random = new Random(seed);
        dividend = Math.abs(random.nextInt() + 4711);
        divisor  = Math.abs(random.nextInt(dividend) + 17);
        longDividend = Math.abs(random.nextLong() + 4711L);
        longDivisor  = Math.abs(random.nextLong() + longDividend);
    }

    @Benchmark
    public double  expDouble() {
        return  Math.exp(double4Dot1);
    }

    @Benchmark
    public double  expDoubleStrict() {
        return  StrictMath.exp(double4Dot1);
    }

    @Benchmark
    public double  logDouble() {
        return  Math.log(double81);
    }

    @Benchmark
    public double  logDoubleStrict() {
        return  StrictMath.log(double81);
    }

    @Benchmark
    public double  log10Double() {
        return  Math.log10(double81);
    }

    @Benchmark
    public double  log10DoubleStrict() {
        return  StrictMath.log10(double81);
    }

    @Benchmark
    public double  powDouble() {
        return  Math.pow(double4Dot1, double2);
    }

    @Benchmark
    public double  powDoubleStrict() {
        return  StrictMath.pow(double4Dot1, double2);
    }

    @Benchmark
    public double  ceilDouble() {
        return  Math.ceil(double4Dot1);
    }

    @Benchmark
    public double  ceilDoubleStrict() {
        return  StrictMath.ceil(double4Dot1);
    }

    @Benchmark
    public double  floorDouble() {
        return  Math.floor(doubleNegative12);
    }

    @Benchmark
    public double  floorDoubleStrict() {
        return  StrictMath.floor(doubleNegative12);
    }

    @Benchmark
    public double  rintDouble() {
        return  Math.rint(double4Dot1);
    }

    @Benchmark
    public double  rintDoubleStrict() {
        return  StrictMath.rint(double4Dot1);
    }

    @Benchmark
    public double  sinDouble() {
        return  Math.sin(double1);
    }

    @Benchmark
    public double  sinDoubleStrict() {
        return  StrictMath.sin(double1);
    }

    @Benchmark
    public double  cosDouble() {
        return  Math.cos(double1);
    }
    @Benchmark
    public double  cosDoubleStrict() {
        return  StrictMath.cos(double1);
    }

    @Benchmark
    public double  tanDouble() {
        return  Math.tan(double1);
    }
    @Benchmark
    public double  tanDoubleStrict() {
        return  StrictMath.tan(double1);
    }

}
