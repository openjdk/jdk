/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304028
 * @summary Tests for {Math, StrictMath}.IEEEremainder
 */

public class IeeeRemainderTests {
    private IeeeRemainderTests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testIeeeRemainderSpecials();
        failures += testIeeeRemainderZeroResult();
        failures += testIeeeRemainderOneResult();
        failures += testIeeeRemainderRounding();

        if (failures > 0) {
            System.err.println("Testing IEEEremainder incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    private static final double NaNd      = Double.NaN;
    private static final double MIN_VALUE = Double.MIN_VALUE;
    private static final double MIN_NORM  = Double.MIN_NORMAL;
    private static final double MAX_VALUE = Double.MAX_VALUE;
    private static final double InfinityD = Double.POSITIVE_INFINITY;

    /**
     * Special cases from the spec interspersed with test cases.
     */
    private static int testIeeeRemainderSpecials() {
        int failures = 0;

        /*
         * If either argument is NaN, or the first argument is
         * infinite, or the second argument is positive zero or
         * negative zero, then the result is NaN.
         *
         */
        for(double nan : Tests.NaNs) {
            failures += testIEEEremainderCase(nan, 1.0, NaNd);
            failures += testIEEEremainderCase(1.0, nan, NaNd);
        }


        double [][] nanResultCases = {
            { InfinityD, InfinityD},
            {-InfinityD, InfinityD},

            { InfinityD, 1.0},
            {-InfinityD, 1.0},

            { InfinityD, NaNd},
            {-InfinityD, NaNd},

            { InfinityD,  0.0},
            {-InfinityD,  0.0},
            { InfinityD, -0.0},
            {-InfinityD, -0.0},
        };

        for(double[] testCase : nanResultCases) {
            failures += testIEEEremainderCase(testCase[0], testCase[1], NaNd);
        }

        /*
         * If the first argument is finite and the second argument is
         * infinite, then the result is the same as the first
         * argument.
         *
         */
        double [] specialCases = {
            +0.0,
            +MIN_VALUE,
            +MIN_NORM,
            +MAX_VALUE,

            -0.0,
            -MIN_VALUE,
            -MIN_NORM,
            -MAX_VALUE,
        };

        double [] infinities = {
            +InfinityD,
            -InfinityD
        };

        for (double specialCase : specialCases) {
            for (double infinity: infinities) {
                failures += testIEEEremainderCase(specialCase, infinity, specialCase);
            }
        }

        return failures;
    }

    private static int testIeeeRemainderZeroResult() {
        int failures = 0;

        double [] testCases = {
            +MIN_VALUE,
            +MIN_NORM,
            +MAX_VALUE*0.5,

            -MIN_VALUE,
            -MIN_NORM,
            -MAX_VALUE*0.5,
        };

        for (double testCase : testCases) {
            /*
             * "If the remainder is zero, its sign is the same as the sign of the first argument."
             */
            failures += testIEEEremainderCase(testCase*2.0, +testCase, Math.copySign(0.0, testCase));
            failures += testIEEEremainderCase(testCase*2.0, -testCase, Math.copySign(0.0, testCase));
        }

        return failures;
    }

    /*
     * Construct test cases where the remainder is one.
     */
    private static int testIeeeRemainderOneResult() {
        int failures = 0;

        double [][] testCases = {
            {4.0,                  3.0},

            {10_001.0,             5000.0},

            {15_001.0,             5000.0},

            {10_000.0,             9999.0},

            {0x1.0p52 + 1.0,       0x1.0p52},

            {0x1.fffffffffffffp52, 0x1.ffffffffffffep52},
        };

        for (var testCase : testCases) {
            failures += testIEEEremainderCase(testCase[0], testCase[1], 1.0);
        }

        return failures;
    }

    /*
     * Test cases that differ in rounding between % and IEEEremainder.
     */
    private static int testIeeeRemainderRounding() {
        int failures = 0;

        double [][] testCases = {
            {3.0,                2.0, -1.0},
            {3.0,               -2.0, -1.0},
        };

        for (var testCase : testCases) {
            failures += testIEEEremainderCase(testCase[0], testCase[1], testCase[2]);
        }

        return failures;
    }

    /*
     * For exact cases, built-in % remainder and IEEE remainder should
     * be the same since the rounding mode in the implicit divide
     * doesn't come into play.
     */
    private static double remainder(double a, double b) {
        return a % b;
    }

    private static int testIEEEremainderCase(double input1, double input2, double expected) {
        int failures = 0;
        failures += Tests.test("StrictMath.IEEEremainder", input1, input2, StrictMath::IEEEremainder, expected);
        failures += Tests.test("Math.IEEEremainder",       input1, input2, Math::IEEEremainder,       expected);

        return failures;
    }
}
