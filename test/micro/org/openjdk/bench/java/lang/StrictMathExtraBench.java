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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class StrictMathExtraBench {

    public static final int SIZE = 1022;

    private static final Random rnd = new Random(42);

    @State(Scope.Thread)
    public static class RangeState {
        final double[] values = new double[SIZE];
        final double min, max;
        final boolean signed;

        public RangeState(boolean signed, double min, double max) {
            this.min = min;
            this.max = max;
            this.signed = signed;
        }

        public RangeState(double min, double max) {
            this(true, min, max);
        }

        @Setup
        public void setup() {
            for (int i = 0; i < values.length; i++) {
                values[i] = rnd.nextDouble(min, max);
                if (signed & rnd.nextBoolean()) {
                    values[i] = -values[i];
                }
            }
        }
    }

    public static class RangeSubnormal extends RangeState {
        public RangeSubnormal() {
            super(0, Double.MIN_NORMAL);
        }
    }

    public static class RangeNormal extends RangeState {
        public RangeNormal() {
            super(Double.MIN_NORMAL, Double.MAX_VALUE);
        }
    }

    public static class RangePositiveSubnormal extends RangeState {
        public RangePositiveSubnormal() {
            super(false, 0, Double.MIN_NORMAL);
        }
    }

    public static class RangePositiveNormal extends RangeState {
        public RangePositiveNormal() {
            super(false, Double.MIN_NORMAL, Double.MAX_VALUE);
        }
    }

    public static class RangePiQuarter extends RangeState {
        public RangePiQuarter() {
            super(Double.MIN_NORMAL, Math.PI / 4);
        }
    }

    public static class RangePiQuarterTo3PiQuarter extends RangeState {
        public RangePiQuarterTo3PiQuarter() {
            super(Math.PI / 4, 3 * Math.PI / 4);
        }
    }

    public static class Range3PiQuarterToPiHalfTwo19 extends RangeState {
        public Range3PiQuarterToPiHalfTwo19() {
            super(3 * Math.PI / 4, 0x1.0p19 * Math.PI / 2);
        }
    }

    public static class RangeBeyondPiHalfTwo19 extends RangeState {
        public RangeBeyondPiHalfTwo19() {
            super(0x1.0p19 * Math.PI / 2, Double.MAX_VALUE);
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void sin_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.sin(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void sin_pi_quarter(Blackhole bh, RangePiQuarter r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.sin(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void sin_3pi_quarter(Blackhole bh, RangePiQuarterTo3PiQuarter r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.sin(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void sin_pi_half_two19(Blackhole bh, Range3PiQuarterToPiHalfTwo19 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.sin(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void sin_beyond_pi_half_two19(Blackhole bh, RangeBeyondPiHalfTwo19 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.sin(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cos_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cos(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cos_pi_quarter(Blackhole bh, RangePiQuarter r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cos(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cos_3pi_quarter(Blackhole bh, RangePiQuarterTo3PiQuarter r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cos(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cos_pi_half_two19(Blackhole bh, Range3PiQuarterToPiHalfTwo19 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cos(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cos_beyond_pi_half_two19(Blackhole bh, RangeBeyondPiHalfTwo19 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cos(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void tan_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.tan(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void tan_pi_quarter(Blackhole bh, RangePiQuarter r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.tan(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void tan_3pi_quarter(Blackhole bh, RangePiQuarterTo3PiQuarter r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.tan(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void tan_pi_half_two19(Blackhole bh, Range3PiQuarterToPiHalfTwo19 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.tan(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void tan_beyond_pi_half_two19(Blackhole bh, RangeBeyondPiHalfTwo19 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.tan(v));
        }
    }

    public static class RangeHalf extends RangeState {
        public RangeHalf() {
            super(Double.MIN_NORMAL, 0.5);
        }
    }

    public static class RangeHalfToOne extends RangeState {
        public RangeHalfToOne() {
            super(0.5, 1.0);
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void asin_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.asin(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void asin_half(Blackhole bh, RangeHalf r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.asin(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void asin_one(Blackhole bh, RangeHalfToOne r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.asin(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void acos_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.acos(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void acos_half(Blackhole bh, RangeHalf r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.acos(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void acos_one(Blackhole bh, RangeHalfToOne r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.acos(v));
        }
    }

    public static class RangeTwo66 extends RangeState {
        public RangeTwo66() {
            super(Double.MIN_NORMAL, 0x1.0p66);
        }
    }

    public static class RangeBeyondTwo66 extends RangeState {
        public RangeBeyondTwo66() {
            super(0x1.0p66, Double.MAX_VALUE);
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void atan_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.atan(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void atan_two66(Blackhole bh, RangeTwo66 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.atan(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void atan_beyond_two66(Blackhole bh, RangeBeyondTwo66 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.atan(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void atan2_subnormal(Blackhole bh, RangeSubnormal r0, RangeSubnormal r1) {
        double[] values0 = r0.values;
        double[] values1 = r1.values;
        for (int i = 0; i < values0.length; i++) {
            bh.consume(StrictMath.atan2(values0[i], values1[i]));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void atan2_normal(Blackhole bh, RangeNormal r0, RangeNormal r1) {
        double[] values0 = r0.values;
        double[] values1 = r1.values;
        for (int i = 0; i < values0.length; i++) {
            bh.consume(StrictMath.atan2(values0[i], values1[i]));
        }
    }

    private static final double LN2 = Math.log(2)/Math.log(Math.E);

    public static class RangeHalfLn2 extends RangeState {
        public RangeHalfLn2() {
            super(Double.MIN_NORMAL, LN2 / 2);
        }
    }

    public static class RangeBeyondHalfLn2 extends RangeState {
        public RangeBeyondHalfLn2() {
            super(LN2 / 2, Double.MAX_VALUE);
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void exp_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.exp(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void exp_half_ln2(Blackhole bh, RangeHalfLn2 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.exp(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void exp_beyond_half_ln2(Blackhole bh, RangeBeyondHalfLn2 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.exp(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void log_subnormal(Blackhole bh, RangePositiveSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.log(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void log_normal(Blackhole bh, RangePositiveNormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.log(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void log10_subnormal(Blackhole bh, RangePositiveSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.log10(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void log10_normal(Blackhole bh, RangePositiveNormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.log10(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void log1p_subnormal(Blackhole bh, RangePositiveSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.log1p(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void log1p_normal(Blackhole bh, RangePositiveNormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.log1p(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cbrt_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cbrt(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cbrt_normal(Blackhole bh, RangeNormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cbrt(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void pow_subnormal(Blackhole bh, RangeSubnormal r0, RangeSubnormal r1) {
        double[] values0 = r0.values;
        double[] values1 = r1.values;
        for (int i = 0; i < values0.length; i++) {
            bh.consume(StrictMath.pow(values0[i], values1[i]));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void pow_normal(Blackhole bh, RangeNormal r0, RangeNormal r1) {
        double[] values0 = r0.values;
        double[] values1 = r1.values;
        for (int i = 0; i < values0.length; i++) {
            bh.consume(StrictMath.pow(values0[i], values1[i]));
        }
    }

    public static class Range22 extends RangeState {
        public Range22() {
            super(Double.MIN_NORMAL, 22);
        }
    }

    public static class RangeBeyond22 extends RangeState {
        public RangeBeyond22() {
            super(22, Double.MAX_VALUE);
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void sinh_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.sinh(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void sinh_22(Blackhole bh, RangeTwoNeg54 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.sinh(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void sinh_beyond_22(Blackhole bh, RangeBeyond22 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.sinh(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cosh_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cosh(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cosh_22(Blackhole bh, RangeTwoNeg54 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cosh(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void cosh_beyond_22(Blackhole bh, RangeBeyond22 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.cosh(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void tanh_subnormal(Blackhole bh, RangeSubnormal r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.tanh(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void tanh_22(Blackhole bh, RangeTwoNeg54 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.tanh(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void hypot_subnormal(Blackhole bh, RangeSubnormal r0, RangeSubnormal r1) {
        double[] values0 = r0.values;
        double[] values1 = r1.values;
        for (int i = 0; i < values0.length; i++) {
            bh.consume(StrictMath.hypot(values0[i], values1[i]));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void hypot_normal(Blackhole bh, RangeNormal r0, RangeNormal r1) {
        double[] values0 = r0.values;
        double[] values1 = r1.values;
        for (int i = 0; i < values0.length; i++) {
            bh.consume(StrictMath.hypot(values0[i], values1[i]));
        }
    }

    public static class RangeTwoNeg54 extends RangeState {
        public RangeTwoNeg54() {
            super(Double.MIN_NORMAL, 0x1.0p-54);
        }
    }

    public static class RangeTwoNeg54ToHalfLn2 extends RangeState {
        public RangeTwoNeg54ToHalfLn2() {
            super(0x1.0p-54, LN2 / 2);
        }
    }

    public static class RangeHalfLn2To56Ln2 extends RangeState {
        public RangeHalfLn2To56Ln2() {
            super(LN2 / 2, 56 * LN2);
        }
    }

    public static class RangeBeyond56Ln2 extends RangeState {
        public RangeBeyond56Ln2() {
            super(56 * LN2, Double.MAX_VALUE);
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void expm1_two_neg_54(Blackhole bh, RangeTwoNeg54 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.expm1(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void expm1_half_ln2(Blackhole bh, RangeTwoNeg54ToHalfLn2 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.expm1(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void expm1_56ln2(Blackhole bh, RangeHalfLn2To56Ln2 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.expm1(v));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    public void expm1_beyond_56ln2(Blackhole bh, RangeBeyond56Ln2 r) {
        double[] values = r.values;
        for (double v : values) {
            bh.consume(StrictMath.expm1(v));
        }
    }

}
