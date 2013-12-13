/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/*
 * @test
 * @bug 8006572
 * @summary Test for use of non-naive summation in stream-related sum and average operations.
 */
public class TestDoubleSumAverage {
    public static void main(String... args) {
        int failures = 0;

        failures += testForCompenstation();
        failures += testZeroAverageOfNonEmptyStream();

        if (failures > 0) {
            throw new RuntimeException("Found " + failures + " numerical failure(s).");
        }
    }

    /**
     * Compute the sum and average of a sequence of double values in
     * various ways and report an error if naive summation is used.
     */
    private static int testForCompenstation() {
        int failures = 0;

        /*
         * The exact sum of the test stream is 1 + 1e6*ulp(1.0) but a
         * naive summation algorithm will return 1.0 since (1.0 +
         * ulp(1.0)/2) will round to 1.0 again.
         */
        double base = 1.0;
        double increment = Math.ulp(base)/2.0;
        int count = 1_000_001;

        double expectedSum = base + (increment * (count - 1));
        double expectedAvg = expectedSum / count;

        // Factory for double a stream of [base, increment, ..., increment] limited to a size of count
        Supplier<DoubleStream> ds = () -> DoubleStream.iterate(base, e -> increment).limit(count);

        DoubleSummaryStatistics stats = ds.get().collect(DoubleSummaryStatistics::new,
                                                         DoubleSummaryStatistics::accept,
                                                         DoubleSummaryStatistics::combine);

        failures += compareUlpDifference(expectedSum, stats.getSum(), 3);
        failures += compareUlpDifference(expectedAvg, stats.getAverage(), 3);

        failures += compareUlpDifference(expectedSum,
                                         ds.get().sum(), 3);
        failures += compareUlpDifference(expectedAvg,
                                         ds.get().average().getAsDouble(), 3);

        failures += compareUlpDifference(expectedSum,
                                         ds.get().boxed().collect(Collectors.summingDouble(d -> d)), 3);
        failures += compareUlpDifference(expectedAvg,
                                         ds.get().boxed().collect(Collectors.averagingDouble(d -> d)),3);
        return failures;
    }

    /**
     * Test to verify that a non-empty stream with a zero average is non-empty.
     */
    private static int testZeroAverageOfNonEmptyStream() {
        Supplier<DoubleStream> ds = () -> DoubleStream.iterate(0.0, e -> 0.0).limit(10);

        return  compareUlpDifference(0.0, ds.get().average().getAsDouble(), 0);
    }

    /**
     * Compute the ulp difference of two double values and compare against an error threshold.
     */
    private static int compareUlpDifference(double expected, double computed, double threshold) {
        double ulpDifference = Math.abs(expected - computed) / Math.ulp(expected);

        if (ulpDifference > threshold) {
            System.err.printf("Numerical summation error too large, %g ulps rather than %g.%n",
                              ulpDifference, threshold);
            return 1;
        } else
            return 0;
    }
}
