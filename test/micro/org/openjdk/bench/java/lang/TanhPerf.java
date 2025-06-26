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

public class TanhPerf {

    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 4, time = 5, timeUnit = TimeUnit.MILLISECONDS)
    @Fork(2)
    @BenchmarkMode(Mode.Throughput)
    @State(Scope.Thread)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public static class TanhPerfRanges {
        public static int tanhInputCount = 2048;

        @Param({"0", "1", "2", "3"})
        public int tanhRangeIndex;

        public double [] tanhPosRandInputs;
        public double [] tanhNegRandInputs;
        public int       tanhInputIndex = 0;
        public double    tanhRangeInputs[][] = {{0.0, 0x1.0P-55}, {0x1.0P-55, 1.0}, {1.0, 22.0}, {22.1, 1.7976931348623157E308} };

        @Setup
        public void setupValues() {
            Random random = new Random(1023);

            // Fill the positive and negative tanh vectors with random values
            tanhPosRandInputs = new double[tanhInputCount];
            tanhNegRandInputs = new double[tanhInputCount];

            for (int i = 0; i < tanhInputCount; i++) {
                double tanhLowerBound = tanhRangeInputs[tanhRangeIndex][0];
                double tanhUpperBound = tanhRangeInputs[tanhRangeIndex][1];
                tanhPosRandInputs[i] = random.nextDouble(tanhLowerBound, tanhUpperBound);
                tanhNegRandInputs[i] = random.nextDouble(-tanhUpperBound, -tanhLowerBound);
            }
        }

        @Benchmark
        @OperationsPerInvocation(2048)
        public double  tanhPosRangeDouble() {
            double res = 0.0;
            for (int i = 0; i < tanhInputCount; i++) {
                res += Math.tanh(tanhPosRandInputs[i]);
            }
            return res;
        }

        @Benchmark
        @OperationsPerInvocation(2048)
        public double  tanhNegRangeDouble() {
            double res = 0.0;
            for (int i = 0; i < tanhInputCount; i++) {
                res += Math.tanh(tanhNegRandInputs[i]);
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
    public static class TanhPerfConstant {
        public static final double constDoubleTiny  = 0x1.0P-57;
        public static final double constDoubleSmall = 0x1.0P-54;
        public static final double constDouble1     = 1.0;
        public static final double constDouble21    = 21.0;
        public static final double constDoubleLarge = 23.0;

        @Benchmark
        public double  tanhConstDoubleTiny() {
            return  Math.tanh(constDoubleTiny);
        }

        @Benchmark
        public double  tanhConstDoubleSmall() {
            return  Math.tanh(constDoubleSmall);
        }

        @Benchmark
        public double  tanhConstDouble1() {
            return  Math.tanh(constDouble1);
        }

        @Benchmark
        public double  tanhConstDouble21() {
            return  Math.tanh(constDouble21);
        }

        @Benchmark
        public double  tanhConstDoubleLarge() {
            return  Math.tanh(constDoubleLarge);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TanhPerfRanges.class.getSimpleName())
                .build();

        new Runner(opt).run();

        opt = new OptionsBuilder()
                .include(TanhPerfConstant.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
