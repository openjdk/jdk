/*
 * Copyright (c) 2023, Azul Systems, Inc. All rights reserved.
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
package org.openjdk.bench.vm.floatingpoint;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Tests for float and double modulo.
 * Testcase is based on: https://github.com/cirosantilli/java-cheat/blob/c5ffd8ea19c5620ce752b6c98b2d3579be2bef98/Nan.java
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class DremFrem {

    private static final int DEFAULT_X_RANGE = 1 << 11;
    private static final int DEFAULT_Y_RANGE = 1 << 11;
    private static boolean regressionValue = false;

    @Benchmark
    @OperationsPerInvocation(DEFAULT_X_RANGE * DEFAULT_Y_RANGE)
    public void calcFloatJava(Blackhole bh) {
        for (int i = 0; i < DEFAULT_X_RANGE; i++) {
            for (int j = DEFAULT_Y_RANGE; j > 0; j--) {
                float x = i;
                float y = j;
                boolean result = (13.0F * x * x * x) % y == 1.0F;
                regressionValue = regressionValue & result;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(DEFAULT_X_RANGE * DEFAULT_Y_RANGE)
    public void calcDoubleJava(Blackhole bh) {
        for (int i = 0; i < DEFAULT_X_RANGE; i++) {
            for (int j = DEFAULT_Y_RANGE; j > 0; j--) {
                double x = i;
                double y = j;
                boolean result = (13.0D * x * x * x) % y == 1.0D;
                regressionValue = regressionValue & result;
            }
        }
    }

    @SuppressWarnings("divzero")
    public void cornercaseFloatJava_divzero(Blackhole bh) {
            assert Float.isNaN(10 / 0);
            assert Float.isNaN(10 / 0);
    }

    @Benchmark
    @OperationsPerInvocation(DEFAULT_X_RANGE * DEFAULT_Y_RANGE)
    public void cornercaseFloatJava(Blackhole bh) {
        for (int i = 0; i < DEFAULT_X_RANGE * DEFAULT_Y_RANGE; i++) {
            // Generate some NaNs.
            float nan            = Float.NaN;
            float zero_div_zero  = 0.0f / 0.0f;
            float sqrt_negative  = (float)Math.sqrt(-1.0);
            float log_negative   = (float)Math.log(-1.0);
            float inf_minus_inf  = Float.POSITIVE_INFINITY - Float.POSITIVE_INFINITY;
            float inf_times_zero = Float.POSITIVE_INFINITY * 0.0f;
            float quiet_nan1     = Float.intBitsToFloat(0x7fc00001);
            float quiet_nan2     = Float.intBitsToFloat(0x7fc00002);
            float signaling_nan1 = Float.intBitsToFloat(0x7fa00001);
            float signaling_nan2 = Float.intBitsToFloat(0x7fa00002);
            float nan_minus      = -nan;

            // Generate some infinities.
            float positive_inf   = Float.POSITIVE_INFINITY;
            float negative_inf   = Float.NEGATIVE_INFINITY;
            float one_div_zero   = 1.0f / 0.0f;
            float log_zero       = (float)Math.log(0.0);

            // Double check that they are actually NaNs.
            assert  Float.isNaN(nan);
            assert  Float.isNaN(zero_div_zero);
            assert  Float.isNaN(sqrt_negative);
            assert  Float.isNaN(inf_minus_inf);
            assert  Float.isNaN(inf_times_zero);
            assert  Float.isNaN(quiet_nan1);
            assert  Float.isNaN(quiet_nan2);
            assert  Float.isNaN(signaling_nan1);
            assert  Float.isNaN(signaling_nan2);
            assert  Float.isNaN(nan_minus);
            assert  Float.isNaN(log_negative);

            // Double check that they are infinities.
            assert  Float.isInfinite(positive_inf);
            assert  Float.isInfinite(negative_inf);
            assert !Float.isNaN(positive_inf);
            assert !Float.isNaN(negative_inf);
            assert one_div_zero == positive_inf;
            assert log_zero == negative_inf;
                // Double check infinities.

            assert Float.isNaN(positive_inf / 10);
            assert Float.isNaN(negative_inf / 10);
            cornercaseFloatJava_divzero(bh);
            assert (+10 / positive_inf) == +10;
            assert (+10 / negative_inf) == +10;
            assert (-10 / positive_inf) == -10;
            assert (-10 / negative_inf) == -10;

            // NaN comparisons always fail.
            // Therefore, all tests that we will do afterwards will be just isNaN.
            assert !(1.0f < nan);
            assert !(1.0f == nan);
            assert !(1.0f > nan);
            assert !(nan == nan);

            // NaN propagate through most operations.
            assert Float.isNaN(nan + 1.0f);
            assert Float.isNaN(1.0f + nan);
            assert Float.isNaN(nan + nan);
            assert Float.isNaN(nan / 1.0f);
            assert Float.isNaN(1.0f / nan);
            assert Float.isNaN((float)Math.sqrt((double)nan));
        }
    }

    @SuppressWarnings("divzero")
    public void cornercaseDoubleJava_divzero(Blackhole bh) {
            assert Double.isNaN(10 / 0);
            assert Double.isNaN(10 / 0);
    }

    @Benchmark
    @OperationsPerInvocation(DEFAULT_X_RANGE * DEFAULT_Y_RANGE)
    public void cornercaseDoubleJava(Blackhole bh) {
        for (int i = 0; i < DEFAULT_X_RANGE * DEFAULT_Y_RANGE; i++) {
            // Generate some NaNs.
            double nan            = Double.NaN;
            double zero_div_zero  = 0.0f / 0.0f;
            double sqrt_negative  = (double)Math.sqrt(-1.0);
            double log_negative   = (double)Math.log(-1.0);
            double inf_minus_inf  = Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY;
            double inf_times_zero = Double.POSITIVE_INFINITY * 0.0f;
            double quiet_nan1     = Double.longBitsToDouble(0x7ffc000000000001L);
            double quiet_nan2     = Double.longBitsToDouble(0x7ffc000000000002L);
            double signaling_nan1 = Double.longBitsToDouble(0x7ffa000000000001L);
            double signaling_nan2 = Double.longBitsToDouble(0x7ffa000000000002L);
            double nan_minus      = -nan;

            // Generate some infinities.
            double positive_inf   = Double.POSITIVE_INFINITY;
            double negative_inf   = Double.NEGATIVE_INFINITY;
            double one_div_zero   = 1.0d / 0.0f;
            double log_zero       = (double)Math.log(0.0);

            // Double check that they are actually NaNs.
            assert  Double.isNaN(nan);
            assert  Double.isNaN(zero_div_zero);
            assert  Double.isNaN(sqrt_negative);
            assert  Double.isNaN(inf_minus_inf);
            assert  Double.isNaN(inf_times_zero);
            assert  Double.isNaN(quiet_nan1);
            assert  Double.isNaN(quiet_nan2);
            assert  Double.isNaN(signaling_nan1);
            assert  Double.isNaN(signaling_nan2);
            assert  Double.isNaN(nan_minus);
            assert  Double.isNaN(log_negative);

            // Double check that they are infinities.
            assert  Double.isInfinite(positive_inf);
            assert  Double.isInfinite(negative_inf);
            assert !Double.isNaN(positive_inf);
            assert !Double.isNaN(negative_inf);
            assert one_div_zero == positive_inf;
            assert log_zero == negative_inf;
                // Double check infinities.

            assert Double.isNaN(positive_inf / 10);
            assert Double.isNaN(negative_inf / 10);
            cornercaseDoubleJava_divzero(bh);
            assert (+10 / positive_inf) == +10;
            assert (+10 / negative_inf) == +10;
            assert (-10 / positive_inf) == -10;
            assert (-10 / negative_inf) == -10;

            // NaN comparisons always fail.
            // Therefore, all tests that we will do afterwards will be just isNaN.
            assert !(1.0d < nan);
            assert !(1.0d == nan);
            assert !(1.0d > nan);
            assert !(nan == nan);

            // NaN propagate through most operations.
            assert Double.isNaN(nan + 1.0d);
            assert Double.isNaN(1.0d + nan);
            assert Double.isNaN(nan + nan);
            assert Double.isNaN(nan / 1.0d);
            assert Double.isNaN(1.0d / nan);
            assert Double.isNaN((double)Math.sqrt((double)nan));
        }
    }

}
