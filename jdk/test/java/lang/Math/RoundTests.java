/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6430675
 * @summary Check for correct implementation of {Math, StrictMath}.round
 */
public class RoundTests {
    public static void main(String... args) {
        int failures = 0;

        failures += testNearFloatHalfCases();
        failures += testNearDoubleHalfCases();

        if (failures > 0) {
            System.err.println("Testing {Math, StrictMath}.round incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    private static int testNearDoubleHalfCases() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.fffffffffffffp-2,  0.0},
            {+0x1.0p-1,              1.0}, // +0.5
            {+0x1.0000000000001p-1,  1.0},

            {-0x1.fffffffffffffp-2,  0.0},
            {-0x1.0p-1,              0.0}, // -0.5
            {-0x1.0000000000001p-1, -1.0},
        };

        for(double[] testCase : testCases) {
            failures += testNearHalfCases(testCase[0], (long)testCase[1]);
        }

        return failures;
    }

    private static int testNearHalfCases(double input, double expected) {
        int failures = 0;

        failures += Tests.test("Math.round",        input, Math.round(input),       expected);
        failures += Tests.test("StrictMath.round",  input, StrictMath.round(input), expected);

        return failures;
    }

    private static int testNearFloatHalfCases() {
        int failures = 0;
        float [][] testCases = {
            {+0x1.fffffep-2f,  0.0f},
            {+0x1.0p-1f,       1.0f}, // +0.5
            {+0x1.000002p-1f,  1.0f},

            {-0x1.fffffep-2f,  0.0f},
            {-0x1.0p-1f,       0.0f}, // -0.5
            {-0x1.000002p-1f, -1.0f},
        };

        for(float[] testCase : testCases) {
            failures += testNearHalfCases(testCase[0], (int)testCase[1]);
        }

        return failures;
    }

    private static int testNearHalfCases(float input, float expected) {
        int failures = 0;

        failures += Tests.test("Math.round",        input, Math.round(input),       expected);
        failures += Tests.test("StrictMath.round",  input, StrictMath.round(input), expected);

        return failures;
    }
}
