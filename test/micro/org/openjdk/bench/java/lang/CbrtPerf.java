/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;

public class CbrtPerf {

    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time = 5, timeUnit = TimeUnit.MILLISECONDS)
    @Fork(2)
    @BenchmarkMode(Mode.Throughput)
    @State(Scope.Thread)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public static class CbrtPerfRanges {
        public static int cbrtInputCount = 2048;

        @Param({"0", "1"})
        public int cbrtRangeIndex;

        public double [] cbrtPosRandInputs;
        public double [] cbrtNegRandInputs;
        public int       cbrtInputIndex = 0;
        public double    cbrtRangeInputs[][] = { {0.0, 0x1.0P-1022}, {0x1.0P-1022, 1.7976931348623157E308} };

        @Setup
        public void setupValues() {
            Random random = new Random(1023);

            // Fill the positive and negative cbrt vectors with random values
            cbrtPosRandInputs = new double[cbrtInputCount];
            cbrtNegRandInputs = new double[cbrtInputCount];

            for (int i = 0; i < cbrtInputCount; i++) {
                double cbrtLowerBound = cbrtRangeInputs[cbrtRangeIndex][0];
                double cbrtUpperBound = cbrtRangeInputs[cbrtRangeIndex][1];
                cbrtPosRandInputs[i] = random.nextDouble(cbrtLowerBound, cbrtUpperBound);
                cbrtNegRandInputs[i] = random.nextDouble(-cbrtUpperBound, -cbrtLowerBound);
            }
        }

        @Benchmark
        @OperationsPerInvocation(2048)
        public double  cbrtPosRangeDouble() {
            double res = 0.0;
            for (int i = 0; i < cbrtInputCount; i++) {
                res += Math.cbrt(cbrtPosRandInputs[i]);
            }
            return res;
        }

        @Benchmark
        @OperationsPerInvocation(2048)
        public double  cbrtNegRangeDouble() {
            double res = 0.0;
            for (int i = 0; i < cbrtInputCount; i++) {
                res += Math.cbrt(cbrtNegRandInputs[i]);
            }
            return res;
        }
    }

    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 4, time = 5, timeUnit = TimeUnit.SECONDS)
    @Fork(2)
    @BenchmarkMode(Mode.Throughput)
    @State(Scope.Thread)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public static class CbrtPerfConstant {
        public static final double constDouble0   = 0.0;
        public static final double constDouble1   = 1.0;
        public static final double constDouble27  = 27.0;
        public static final double constDouble512 = 512.0;

        @Benchmark
        public double  cbrtConstDouble0() {
            return  Math.cbrt(constDouble0);
        }

        @Benchmark
        public double  cbrtConstDouble1() {
            return  Math.cbrt(constDouble1);
        }

        @Benchmark
        public double  cbrtConstDouble27() {
            return  Math.cbrt(constDouble27);
        }

        @Benchmark
        public double  cbrtConstDouble512() {
            return  Math.cbrt(constDouble512);
        }
    }

    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 4, time = 5, timeUnit = TimeUnit.SECONDS)
    @Fork(2)
    @BenchmarkMode(Mode.Throughput)
    @State(Scope.Thread)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public static class CbrtPerfSpecialValues {
        public double double0           = 0.0;
        public double doubleNegative0   = -0.0;
        public double doubleInf         = Double.POSITIVE_INFINITY;
        public double doubleNegativeInf = Double.NEGATIVE_INFINITY;
        public double doubleNaN         = Double.NaN;

        @Benchmark
        public double  cbrtDouble0() {
            return  Math.cbrt(double0);
        }

        @Benchmark
        public double  cbrtDoubleNegative0() {
            return  Math.cbrt(doubleNegative0);
        }

        @Benchmark
        public double  cbrtDoubleInf() {
            return  Math.cbrt(doubleInf);
        }

        @Benchmark
        public double  cbrtDoubleNegativeInf() {
            return  Math.cbrt(doubleNegativeInf);
        }

        @Benchmark
        public double  cbrtDoubleNaN() {
            return  Math.cbrt(doubleNaN);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CbrtPerfRanges.class.getSimpleName())
                .build();

        new Runner(opt).run();

        opt = new OptionsBuilder()
                .include(CbrtPerfConstant.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
