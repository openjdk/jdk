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

public class SinhPerf {

    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time = 5, timeUnit = TimeUnit.MILLISECONDS)
    @Fork(2)
    @BenchmarkMode(Mode.Throughput)
    @State(Scope.Thread)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public static class SinhPerfRanges {
        public static int sinhInputCount = 2048;

        @Param({"0", "1", "2", "3", "4"})
        public int sinhRangeIndex;

        public double [] sinhPosRandInputs;
        public double [] sinhNegRandInputs;
        public int       sinhInputIndex = 0;
        public double    sinhRangeInputs[][] = { {0.0, 0x1.0P-28},
                                                 {0x1.0P-28, 22.0},
                                                 {22.0, Math.log(Double.MAX_VALUE)},
                                                 {Math.log(Double.MAX_VALUE), Double.longBitsToDouble(0x408633CE8FB9F87DL)},
                                                 {Double.longBitsToDouble(0x408633CE8FB9F87DL), Double.MAX_VALUE} };

        @Setup
        public void setupValues() {
            Random random = new Random(1023);

            // Fill the positive and negative sinh vectors with random values
            sinhPosRandInputs = new double[sinhInputCount];
            sinhNegRandInputs = new double[sinhInputCount];

            for (int i = 0; i < sinhInputCount; i++) {
                double sinhLowerBound = sinhRangeInputs[sinhRangeIndex][0];
                double sinhUpperBound = sinhRangeInputs[sinhRangeIndex][1];
                sinhPosRandInputs[i] = random.nextDouble(sinhLowerBound, sinhUpperBound);
                sinhNegRandInputs[i] = random.nextDouble(-sinhUpperBound, -sinhLowerBound);
            }
        }

        @Benchmark
        @OperationsPerInvocation(2048)
        public double  sinhPosRangeDouble() {
            double res = 0.0;
            for (int i = 0; i < sinhInputCount; i++) {
                res += Math.sinh(sinhPosRandInputs[i]);
            }
            return res;
        }

        @Benchmark
        @OperationsPerInvocation(2048)
        public double  sinhNegRangeDouble() {
            double res = 0.0;
            for (int i = 0; i < sinhInputCount; i++) {
                res += Math.sinh(sinhNegRandInputs[i]);
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
    public static class SinhPerfConstant {
        public static final double constDoubleTiny  = 0x1.0P-30;
        public static final double constDoubleSmall = 0x1.0P-27;
        public static final double constDouble21    = 21.0;
        public static final double constDouble23    = 23.0;
        public static final double constDoubleLarge = Math.log(Double.MAX_VALUE) + 0.5;
        public static final double constDoubleOverflow = 0x1.0P10;

        @Benchmark
        public double  sinhConstDoubleTiny() {
            return  Math.sinh(constDoubleTiny);
        }

        @Benchmark
        public double  sinhConstDoubleSmall() {
            return  Math.sinh(constDoubleSmall);
        }

        @Benchmark
        public double  sinhConstDouble21() {
            return  Math.sinh(constDouble21);
        }

        @Benchmark
        public double  sinhConstDouble23() {
            return  Math.sinh(constDouble23);
        }

        @Benchmark
        public double  sinhConstDoubleLarge() {
            return  Math.sinh(constDoubleLarge);
        }

        @Benchmark
        public double  sinhConstDoubleOverflow() {
            return  Math.sinh(constDoubleOverflow);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SinhPerfRanges.class.getSimpleName())
                .build();

        new Runner(opt).run();

        opt = new OptionsBuilder()
                .include(SinhPerfConstant.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
