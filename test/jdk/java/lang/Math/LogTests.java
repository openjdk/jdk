/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8301202
 * @build Tests
 * @build LogTests
 * @run main LogTests
 * @summary Tests for {Math, StrictMath}.log
 */

public class LogTests {
    private LogTests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testLogSpecialCases();
        failures += testLogMonotonicityNeighborhoods();
        failures += testLogMinValueEquality();

        if (failures > 0) {
            System.err.println("Testing log incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    private static final double infinityD = Double.POSITIVE_INFINITY;
    private static final double NaNd = Double.NaN;

    /**
     * From the spec for Math.log:
     * "Special cases:
     *
     * If the argument is NaN or less than zero, then the result is NaN.
     * If the argument is positive infinity, then the result is positive infinity.
     * If the argument is positive zero or negative zero, then the result is negative infinity.
     * If the argument is 1.0, then the result is positive zero.
     */
    private static int testLogSpecialCases() {
        int failures = 0;

        double [][] testCases = {
            {Double.NaN,                NaNd},
            {Double.NEGATIVE_INFINITY,  NaNd},
            {-Double.MAX_VALUE,         NaNd},
            {-1.0,                      NaNd},
            {-Double.MIN_NORMAL,        NaNd},
            {-Double.MIN_VALUE,         NaNd},

            {Double.POSITIVE_INFINITY,  infinityD},

            {-0.0,                      -infinityD},
            {+0.0,                      -infinityD},

            {+1.0,                      0.0},
        };

        for(int i = 0; i < testCases.length; i++) {
            failures += testLogCase(testCases[i][0],
                                    testCases[i][1]);
        }

        return failures;
    }

    private static int testLogCase(double input, double expected) {
        int failures=0;

        failures+=Tests.test("Math.log",       input, Math::log,       expected);
        failures+=Tests.test("StrictMath.log", input, StrictMath::log, expected);

        return failures;
    }

    /**
     * Neighborhood monotonicity: probe tight neighborhoods around
     * exp(k) and powers-of-two boundaries using nextDown/nextUp.
     * For each center value c, build array:
     * {nextDown(nextDown(c)), nextDown(c), c, nextUp(c), nextUp(nextUp(c))}
     * and assert non-decreasing order for Math.log and StrictMath.log.
     */
    private static int testLogMonotonicityNeighborhoods() {
        int failures = 0;
        // exp(k) neighborhoods
        failures += probeNeighborhoods(k -> StrictMath.pow(Math.E, k));
        // 2^k neighborhoods
        failures += probeNeighborhoods(k -> Math.scalb(1.0, k));
        return failures;
    }

    private static int probeNeighborhoods(java.util.function.IntToDoubleFunction centerFunc) {
        int failures = 0;
        for (int k = -36; k <= 36; k++) {
            double c = centerFunc.applyAsDouble(k);
            if (!(c > 0.0) || Double.isInfinite(c) || Double.isNaN(c)) continue; // skip invalid centers
            double[] n = new double[5];
            n[2] = c;
            n[1] = Math.nextDown(c);
            n[0] = Math.nextDown(n[1]);
            n[3] = Math.nextUp(c);
            n[4] = Math.nextUp(n[3]);
            double[] ml = new double[5];
            double[] sl = new double[5];
            for (int i = 0; i < n.length; i++) {
                ml[i] = Math.log(n[i]);
                sl[i] = StrictMath.log(n[i]);
            }
            for (int i = 0; i < n.length - 1; i++) {
                if (ml[i] > ml[i+1]) {
                    failures++;
                    System.err.println("Monotonicity failure Math.log at k=" + k + " values " + n[i] + ", " + n[i+1]);
                }
                if (sl[i] > sl[i+1]) {
                    failures++;
                    System.err.println("Monotonicity failure StrictMath.log at k=" + k + " values " + n[i] + ", " + n[i+1]);
                }
            }
        }
        return failures;
    }

    /**
     * Equality check: Math.log(Double.MIN_VALUE) should equal StrictMath.log(Double.MIN_VALUE)
     * (Used to be a standalone TestLogMinValue.java). Ensures intrinsic does not diverge at subnormal minimum.
     */
    private static int testLogMinValueEquality() {
        int failures = 0;
        double x = Double.MIN_VALUE;
        double mathLog = Math.log(x);
        double strictLog = StrictMath.log(x);
        if (Double.doubleToRawLongBits(mathLog) != Double.doubleToRawLongBits(strictLog)) {
            failures++;
            System.err.println("Mismatch: Math.log(Double.MIN_VALUE)=" + mathLog +
                               " StrictMath.log(Double.MIN_VALUE)=" + strictLog);
        }
        return failures;
    }
}
