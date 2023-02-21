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
 * @bug 4984407 8302028
 * @summary Tests for {Math, StrictMath}.atan2
 */

public class Atan2Tests {
    private Atan2Tests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testAtan2();

        if (failures > 0) {
            System.err.println("Testing atan2 incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    /**
     * Special cases from the spec interspersed with test cases.
     */
    private static int testAtan2() {
        int failures = 0;
        double NaNd      = Double.NaN;
        double MIN_VALUE = Double.MIN_VALUE;
        double MIN_NORM  = Double.MIN_NORMAL;
        double MAX_VALUE = Double.MAX_VALUE;
        double InfinityD = Double.POSITIVE_INFINITY;
        double PI        = Math.PI;

        /*
         * If either argument is NaN, then the result is NaN.
         */
        for(double nan : Tests.NaNs) {
            failures += testAtan2Case(nan, 0.0, NaNd);
            failures += testAtan2Case(0.0, nan, NaNd);
        }

        double [][] testCases = {
            /*
             * If the first argument is positive zero and the second
             * argument is positive, or the first argument is positive
             * and finite and the second argument is positive
             * infinity, then the result is positive zero.
             */
            {+0.0,       MIN_VALUE, +0.0},
            {+0.0,       MIN_NORM,  +0.0},
            {+0.0,       1.0,       +0.0},
            {+0.0,       MAX_VALUE, +0.0},
            {+0.0,       InfinityD, +0.0},

            {MIN_VALUE,  InfinityD, +0.0},
            {MIN_NORM,   InfinityD, +0.0},
            {1.0,        InfinityD, +0.0},
            {MAX_VALUE,  InfinityD, +0.0},
            {MIN_VALUE,  InfinityD, +0.0},

            /*
             * If the first argument is negative zero and the second
             * argument is positive, or the first argument is negative
             * and finite and the second argument is positive
             * infinity, then the result is negative zero.
             */
            {-0.0,       MIN_VALUE, -0.0},
            {-0.0,       MIN_NORM,  -0.0},
            {-0.0,       1.0,       -0.0},
            {-0.0,       MAX_VALUE, -0.0},
            {-0.0,       InfinityD, -0.0},

            {-MIN_VALUE, InfinityD, -0.0},
            {-MIN_NORM,  InfinityD, -0.0},
            {-1.0,       InfinityD, -0.0},
            {-MAX_VALUE, InfinityD, -0.0},

            /*
             * If the first argument is positive zero and the second
             * argument is negative, or the first argument is positive
             * and finite and the second argument is negative
             * infinity, then the result is the double value closest
             * to pi.
             */
            {+0.0,      -MIN_VALUE, PI},
            {+0.0,      -MIN_NORM,  PI},
            {+0.0,      -1.0,       PI},
            {+0.0,      -MAX_VALUE, PI},
            {+0.0,      -InfinityD, PI},

            {MIN_VALUE, -InfinityD, PI},
            {MIN_NORM,  -InfinityD, PI},
            {1.0,       -InfinityD, PI},
            {MAX_VALUE, -InfinityD, PI},

            /*
             * If the first argument is negative zero and the second
             * argument is negative, or the first argument is negative
             * and finite and the second argument is negative
             * infinity, then the result is the double value closest
             * to -pi.
             */
            {-0.0,      -MIN_VALUE, -PI},
            {-0.0,      -MIN_NORM,  -PI},
            {-0.0,      -1.0,       -PI},
            {-0.0,      -MAX_VALUE, -PI},
            {-0.0,      -InfinityD, -PI},

            {-MIN_VALUE, -InfinityD, -PI},
            {-MIN_NORM,  -InfinityD, -PI},
            {-1.0,       -InfinityD, -PI},
            {-MAX_VALUE, -InfinityD, -PI},

            /*
             * If the first argument is positive and the second
             * argument is positive zero or negative zero, or the
             * first argument is positive infinity and the second
             * argument is finite, then the result is the double value
             * closest to pi/2.
             */
            {MIN_VALUE,  +0.0,        PI/2.0},
            {MIN_NORM,   +0.0,        PI/2.0},
            {1.0,        +0.0,        PI/2.0},
            {MAX_VALUE,  +0.0,        PI/2.0},

            {MIN_VALUE,  -0.0,        PI/2.0},
            {MIN_VALUE,  -0.0,        PI/2.0},
            {MIN_NORM,   -0.0,        PI/2.0},
            {1.0,        -0.0,        PI/2.0},
            {MAX_VALUE,  -0.0,        PI/2.0},

            {InfinityD,  -MIN_VALUE,  PI/2.0},
            {InfinityD,  -MIN_NORM,   PI/2.0},
            {InfinityD,  -1.0,        PI/2.0},
            {InfinityD,  -MAX_VALUE,  PI/2.0},

            {InfinityD,  MIN_VALUE,   PI/2.0},
            {InfinityD,  MIN_NORM,    PI/2.0},
            {InfinityD,  1.0,         PI/2.0},
            {InfinityD,  MAX_VALUE,   PI/2.0},

            /*
             * If the first argument is negative and the second argument is
             * positive zero or negative zero, or the first argument is
             * negative infinity and the second argument is finite, then the
             * result is the double value closest to -pi/2.
             */
            {-MIN_VALUE,  +0.0,        -PI/2.0},
            {-MIN_NORM,   +0.0,        -PI/2.0},
            {-1.0,        +0.0,        -PI/2.0},
            {-MAX_VALUE,  +0.0,        -PI/2.0},

            {-MIN_VALUE,  -0.0,        -PI/2.0},
            {-MIN_VALUE,  -0.0,        -PI/2.0},
            {-MIN_NORM,   -0.0,        -PI/2.0},
            {-1.0,        -0.0,        -PI/2.0},
            {-MAX_VALUE,  -0.0,        -PI/2.0},

            {-InfinityD,  -MIN_VALUE,  -PI/2.0},
            {-InfinityD,  -MIN_NORM,   -PI/2.0},
            {-InfinityD,  -1.0,        -PI/2.0},
            {-InfinityD,  -MAX_VALUE,  -PI/2.0},

            {-InfinityD,  MIN_VALUE,   -PI/2.0},
            {-InfinityD,  MIN_NORM,    -PI/2.0},
            {-InfinityD,  1.0,         -PI/2.0},
            {-InfinityD,  MAX_VALUE,   -PI/2.0},

            /*
             * If both arguments are positive infinity, then the result is the
             * double value closest to pi/4.
             */
            {InfinityD,  InfinityD,     PI/4.0},

            /*
             * If the first argument is positive infinity and the
             * second argument is negative infinity, then the result
             * is the double value closest to 3*pi/4.
             */
            // Note: in terms of computation, the result of the double
            // expression
            //   3*PI/4.0
            // is the same as a high-precision decimal value of pi
            // scaled accordingly and rounded to double:
            //   BigDecimal bdPi = new BigDecimal("3.14159265358979323846264338327950288419716939937510");
            //   bdPi.multiply(BigDecimal.valueOf(3)).divide(BigDecimal.valueOf(4)).doubleValue();
            {InfinityD,  -InfinityD,     3*PI/4.0},

            /*
             * If the first argument is negative infinity and the second
             * argument is positive infinity, then the result is the double
             * value closest to -pi/4.
             */
            {-InfinityD,  InfinityD,     -PI/4.0},

            /*
             * If both arguments are negative infinity, then the result is the
             * double value closest to -3*pi/4.
             */
            {-InfinityD,  -InfinityD,     -3*PI/4.0},

            {-3.0,         InfinityD,     -0.0},
        };

        for (double[] testCase : testCases) {
            failures += testAtan2Case(testCase[0], testCase[1], testCase[2]);
        }

        return failures;
    }

    private static int testAtan2Case(double input1, double input2, double expected) {
        int failures = 0;
        failures += Tests.test("StrictMath.atan2", input1, input2, StrictMath::atan2, expected);
        failures += Tests.test("Math.atan2",       input1, input2, Math::atan2,       expected);

        return failures;
    }
}
