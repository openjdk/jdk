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
}
