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
 */

import java.util.Random;
import java.util.stream.DoubleStream;

public class CompensatedSums {

    public static void main(String [] args) {
        double naive = 0;
        double sequentialStream = 0;
        double parallelStream = 0;
        double mySequentialStream = 0;
        double myParallelStream = 0;

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

            // squared error of naive sum by reduction - should be large
            naive += Math.pow(DoubleStream.of(rand).reduce((x, y) -> x+y).getAsDouble() - sum[0], 2);

            // squared error of sequential sum - should be 0
            sequentialStream += Math.pow(DoubleStream.of(rand).sum() - sum[0], 2);

            // squared error of parallel sum
            parallelStream += Math.pow(DoubleStream.of(rand).parallel().sum() - sum[0], 2);

            // squared error of modified sequential sum - should be 0
            mySequentialStream += Math.pow(computeFinalSum(DoubleStream.of(rand).collect(
                    () -> new double[3],
                    (ll, d) -> {
                        sumWithCompensation(ll, d);
                        ll[2] += d;
                    },
                    (ll, rr) -> {
                        sumWithCompensation(ll, rr[0]);
                        sumWithCompensation(ll, -rr[1]); // minus is added
                        ll[2] += rr[2];
                    })) - sum[0], 2);

            // squared error of modified parallel sum - typically ~0.25-0.5 times squared error of parallel sum
            myParallelStream += Math.pow(computeFinalSum(DoubleStream.of(rand).parallel().collect(
                    () -> new double[3],
                    (ll, d) -> {
                        sumWithCompensation(ll, d);
                        ll[2] += d;
                    },
                    (ll, rr) -> {
                        sumWithCompensation(ll, rr[0]);
                        sumWithCompensation(ll, -rr[1]); // minus is added
                        ll[2] += rr[2];
                    })) - sum[0], 2);
        }

        // print sum of squared errors
        System.out.println(naive);
        System.out.println(sequentialStream);
        System.out.println(parallelStream);
        System.out.println(mySequentialStream);
        System.out.println(myParallelStream);
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

}