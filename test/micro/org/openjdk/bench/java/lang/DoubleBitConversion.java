/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.BitSet;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(3)
public class DoubleBitConversion {

    double doubleZero = 0;
    double doubleOne = 1;
    double doubleNan = Double.NaN;

    long longDoubleZero = Double.doubleToLongBits(0);
    long longDoubleOne = Double.doubleToLongBits(1);
    long longDoubleNaN = Double.doubleToLongBits(Double.NaN);

    @Benchmark
    public long doubleToRawLongBits_zero() {
        return Double.doubleToRawLongBits(doubleZero);
    }

    @Benchmark
    public long doubleToRawLongBits_one() {
        return Double.doubleToRawLongBits(doubleOne);
    }

    @Benchmark
    public long doubleToRawLongBits_NaN() {
        return Double.doubleToRawLongBits(doubleNan);
    }

    @Benchmark
    public long doubleToLongBits_zero() {
        return Double.doubleToLongBits(doubleZero);
    }

    @Benchmark
    public long doubleToLongBits_one() {
        return Double.doubleToLongBits(doubleOne);
    }

    @Benchmark
    public long doubleToLongBits_NaN() {
        return Double.doubleToLongBits(doubleNan);
    }

    @Benchmark
    public double longBitsToDouble_zero() {
        return Double.longBitsToDouble(longDoubleZero);
    }

    @Benchmark
    public double longBitsToDouble_one() {
        return Double.longBitsToDouble(longDoubleOne);
    }

    @Benchmark
    public double longBitsToDouble_NaN() {
        return Double.longBitsToDouble(longDoubleNaN);
    }

}

