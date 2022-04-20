/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8214761
 * @run testng CompensatedSums
 * @summary
 */

import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CompensatedSums {

    @Test
    public void testCompensatedSums() {
        double naive = 0;
        double jdkSequentialStreamError = 0;
        double goodSequentialStreamError = 0;
        double jdkParallelStreamError = 0;
        double goodParallelStreamError = 0;
        double badParallelStreamError = 0;

        for (int loop = 0; loop < 100; loop++) {
            // sequence of random numbers of varying magnitudes, both positive and negative
            double[] rand = new Random().doubles(1_000_000)
                    .map(Math::log)
                    .map(x -> (Double.doubleToLongBits(x) % 2 == 0) ? x : -x)
                    .toArray();

            // base case: standard Kahan summation
            double[] sum = new double[2];
            for (int i=0; i < rand.length; i++) {
                sumWithCompensation(sum, rand[i]);
            }

            // All error is the squared difference of the standard Kahan Sum vs JDK Stream sum implementation
            // Older less accurate implementations included here as the baseline.

            // squared error of naive sum by reduction - should be large
            naive += square(DoubleStream.of(rand).reduce((x, y) -> x+y).getAsDouble() - sum[0]);

            // squared error of sequential sum - should be 0
            jdkSequentialStreamError += square(DoubleStream.of(rand).sum() - sum[0]);

            goodSequentialStreamError += square(computeFinalSum(DoubleStream.of(rand).collect(doubleSupplier,objDoubleConsumer,goodCollectorConsumer)) - sum[0]);

            // squared error of parallel sum from the JDK
            jdkParallelStreamError += square(DoubleStream.of(rand).parallel().sum() - sum[0]);

            // squared error of parallel sum
            goodParallelStreamError += square(computeFinalSum(DoubleStream.of(rand).parallel().collect(doubleSupplier,objDoubleConsumer,goodCollectorConsumer)) - sum[0]);

            // the bad parallel stream
            badParallelStreamError += square(computeFinalSum(DoubleStream.of(rand).parallel().collect(doubleSupplier,objDoubleConsumer,badCollectorConsumer)) - sum[0]);


        }

        Assert.assertTrue(jdkParallelStreamError <= goodParallelStreamError);
        Assert.assertTrue(badParallelStreamError > jdkParallelStreamError);

        Assert.assertTrue(goodSequentialStreamError >= jdkSequentialStreamError);
        Assert.assertTrue(naive > jdkSequentialStreamError);
        Assert.assertTrue(naive > jdkParallelStreamError);

    }

    private static double square(double arg) {
        return arg * arg;
    }

    // from OpenJDK8 Collectors, unmodified
    static double[] sumWithCompensation(double[] intermediateSum, double value) {
        double tmp = value - intermediateSum[1];
        double sum = intermediateSum[0];
        double velvel = sum + tmp; // Little wolf of rounding error
        intermediateSum[1] = (velvel - sum) - tmp;
        intermediateSum[0] = velvel;
        return intermediateSum;
    }

    // from OpenJDK8 Collectors, unmodified
    static double computeFinalSum(double[] summands) {
        double tmp = summands[0] + summands[1];
        double simpleSum = summands[summands.length - 1];
        if (Double.isNaN(tmp) && Double.isInfinite(simpleSum))
            return simpleSum;
        else
            return tmp;
    }

    //Suppliers and consumers for Double Stream summation collection.
    static Supplier<double[]> doubleSupplier = () -> new double[3];
    static ObjDoubleConsumer<double[]> objDoubleConsumer = (double[] ll, double d) -> {
                                                             sumWithCompensation(ll, d);
                                                             ll[2] += d;
                                                           };
    static BiConsumer<double[], double[]> badCollectorConsumer =
            (ll, rr) -> {
                sumWithCompensation(ll, rr[0]);
                sumWithCompensation(ll, rr[1]);
                ll[2] += rr[2];
            };

    static BiConsumer<double[], double[]> goodCollectorConsumer =
            (ll, rr) -> {
                sumWithCompensation(ll, rr[0]);
                sumWithCompensation(ll, -rr[1]);
                ll[2] += rr[2];
            };

}