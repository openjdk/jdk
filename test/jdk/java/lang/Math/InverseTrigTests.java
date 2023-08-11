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
 * @bug 8302026
 * @build Tests
 * @build InverseTrigTests
 * @run main InverseTrigTests
 * @summary Tests for {Math, StrictMath}.{asin, acos, atan}
 */

import static java.lang.Double.longBitsToDouble;

public class InverseTrigTests {
    private InverseTrigTests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testAsinSpecialCases();
        failures += testAcosSpecialCases();
        failures += testAtanSpecialCases();

        if (failures > 0) {
            System.err.println("Testing inverse trig mthods incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    private static final double InfinityD = Double.POSITIVE_INFINITY;
    private static final double NaNd = Double.NaN;

    /**
     * From the spec for Math.asin:
     *
     * "Special cases:
     *
     * If the argument is NaN or its absolute value is greater than 1,
     * then the result is NaN.
     *
     * If the argument is zero, then the result is a zero with the
     * same sign as the argument."
     */
    private static int testAsinSpecialCases() {
        int failures = 0;

        for(double nan : Tests.NaNs) {
            failures += testAsinCase(nan, NaNd);
        }

        double [][] testCases = {
            {Math.nextUp(1.0),    NaNd},
            {Math.nextDown(-1.0), NaNd},
            { InfinityD,          NaNd},
            {-InfinityD,          NaNd},

            {-0.0,                -0.0},
            {+0.0,                +0.0},
        };

        for(int i = 0; i < testCases.length; i++) {
            failures += testAsinCase(testCases[i][0],
                                     testCases[i][1]);
        }

        return failures;
    }

    private static int testAsinCase(double input, double expected) {
        int failures=0;

        failures += Tests.test("Math.asin",       input, Math::asin,       expected);
        failures += Tests.test("StrictMath.asin", input, StrictMath::asin, expected);

        return failures;
    }

    /**
     * From the spec for Math.acos:
     *
     * "Special case:
     *
     * If the argument is NaN or its absolute value is greater than 1,
     * then the result is NaN.
     *
     * If the argument is 1.0, the result is positive zero."
     */
    private static int testAcosSpecialCases() {
        int failures = 0;

        for(double nan : Tests.NaNs) {
            failures += testAcosCase(nan, NaNd);
        }

        double [][] testCases = {
            {Math.nextUp(1.0),    NaNd},
            {Math.nextDown(-1.0), NaNd},
            {InfinityD,           NaNd},
            {-InfinityD,          NaNd},

            {1.0,                 +0.0},
        };

        for(int i = 0; i < testCases.length; i++) {
            failures += testAcosCase(testCases[i][0],
                                     testCases[i][1]);
        }

        return failures;
    }

    private static int testAcosCase(double input, double expected) {
        int failures=0;

        failures += Tests.test("Math.acos",       input, Math::acos,       expected);
        failures += Tests.test("StrictMath.acos", input, StrictMath::acos, expected);

        return failures;
    }

    /**
     * From the spec for Math.atan:
     *
     * "Special cases:
     *
     * If the argument is NaN, then the result is NaN.
     *
     * If the argument is zero, then the result is a zero with the
     * same sign as the argument.
     *
     * If the argument is infinite, then the result is the closest
     * value to pi/2 with the same sign as the input."
     */
    private static int testAtanSpecialCases() {
        int failures = 0;

        for(double nan : Tests.NaNs) {
            failures += testAtanCase(nan, NaNd);
        }

        double [][] testCases = {
            {-0.0,       -0.0},
            {+0.0,       +0.0},

            { InfinityD, +Math.PI/2.0},
            {-InfinityD, -Math.PI/2.0},
        };

        for(int i = 0; i < testCases.length; i++) {
            failures += testAtanCase(testCases[i][0],
                                     testCases[i][1]);
        }

        return failures;
    }

    private static int testAtanCase(double input, double expected) {
        int failures=0;

        failures += Tests.test("Math.atan",       input, Math::atan,       expected);
        failures += Tests.test("StrictMath.atan", input, StrictMath::atan, expected);

        return failures;
    }
}
