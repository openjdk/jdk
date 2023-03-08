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
 * @library /test/lib
 * @build Tests
 * @run main SqrtTests
 * @bug 8302040
 * @summary Tests for {Math, StrictMath}.sqrt
 */

public class SqrtTests {
    private SqrtTests(){}

    public static void main(String... argv) {
        int failures = 0;

        failures += testSqrt();

        if (failures > 0) {
            System.err.println("Testing sqrt incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    private static final double InfinityD = Double.POSITIVE_INFINITY;
    private static final double NaNd      = Double.NaN;

    /**
     * "Returns the correctly rounded positive square root of a double value. Special cases:
     *
     * If the argument is NaN or less than zero, then the result is NaN.
     *
     * If the argument is positive infinity, then the result is positive infinity.
     *
     * If the argument is positive zero or negative zero, then the
     * result is the same as the argument.
     *
     * Otherwise, the result is the double value closest to the true
     * mathematical square root of the argument value."
     */
    private static int testSqrt() {
        int failures = 0;

        for(double nan : Tests.NaNs) {
            failures += testSqrtCase(nan, NaNd);
        }

        double [][] testCases = {
            {InfinityD,               InfinityD},

            {-Double.MIN_VALUE,       NaNd},
            {-Double.MIN_NORMAL,      NaNd},
            {-Double.MAX_VALUE,       NaNd},
            {-InfinityD,              NaNd},

            {+0.0,                   +0.0},
            {-0.0,                   -0.0},

            // Test some notable perfect squares
            {+0.25,                   +0.5},
            {+1.0,                    +1.0},
            {+4.0,                    +2.0},
            {+9.0,                    +3.0},
            {+0x1.ffffff0000002p1023, +0x1.ffffff8p511}
        };

        for(int i = 0; i < testCases.length; i++) {
            failures += testSqrtCase(testCases[i][0], testCases[i][1]);
        }

        return failures;
    }

    private static int testSqrtCase(double input, double expected) {
        int failures=0;

        failures+=Tests.test("Math.sqrt",        input, Math::sqrt,        expected);
        failures+=Tests.test("StrictMath.sqrt",  input, StrictMath::sqrt,  expected);

        return failures;
    }
}
