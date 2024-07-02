/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4851638
 * @key randomness
 * @summary Tests for StrictMath.atan2
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build Tests
 * @build FdlibmTranslit
 * @build Atan2Tests
 * @run main Atan2Tests
 */

import jdk.test.lib.RandomFactory;

/**
 * The tests in ../Math/Atan2Tests.java test properties that should
 * hold for any atan2 implementation, including the FDLIBM-based one
 * required for StrictMath.atan2.  Therefore, the test cases in
 * ../Math/Atan2Tests.java are run against both the Math and
 * StrictMath versions of atan2.  The role of this test is to verify
 * that the FDLIBM atan2 algorithm is being used by running golden
 * file tests on values that may vary from one conforming atan2
 * implementation to another.
 */

public class Atan2Tests {
    private Atan2Tests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testAtan2();
        failures += testAgainstTranslit();

        if (failures > 0) {
            System.err.println("Testing atan2 incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    private static int testAtan2() {
        int failures = 0;

        // Empirical worst-case points in other libraries with larger
        // worst-case errors than FDLIBM
        double[][] testCases = {
            {-0x0.00000000039a2p-1022, 0x0.000fdf02p-1022,     -0x1.d0ce6fac85de9p-27},
            { 0x1.9173ea8221453p+842,  0x1.8c6f1b4b72f3ap+842,  0x1.9558272cbbdf9p-1},
            { 0x1.9cde4ff190e45p+931,  0x1.37d91467e558bp+931,  0x1.d909432d6f9c8p-1},
            { 0x1.401ec07d65549p+888,  0x1.3c3976605bb0cp+888,  0x1.95421cda9c65bp-1},
        };

        for (double[] testCase : testCases) {
            failures += testAtan2Case(testCase[0], testCase[1], testCase[2]);
        }
        return failures;
    }

    // Initialize shared random number generator
    private static java.util.Random random = RandomFactory.getRandom();

    /**
     * Test StrictMath.atan2 against transliteration port of atan2.
     */
    private static int testAgainstTranslit() {
        int failures = 0;

        double MIN_VALUE = Double.MIN_VALUE;
        double MIN_NORM  = Double.MIN_NORMAL;
        double MAX_VALUE = Double.MAX_VALUE;
        double InfinityD = Double.POSITIVE_INFINITY;
        double PI        = Math.PI;

        // The exact special cases for infinity, NaN, zero,
        // etc. inputs are checked in the Math tests.

        // Test exotic NaN bit patterns
        double[][] exoticNaNs = {
            {Double.longBitsToDouble(0x7FF0_0000_0000_0001L), 0.0},
            {0.0, Double.longBitsToDouble(0x7FF0_0000_0000_0001L)},
            {Double.longBitsToDouble(0xFFF_00000_0000_0001L), 0.0},
            {0.0, Double.longBitsToDouble(0xFFF0_0000_0000_0001L)},
            {Double.longBitsToDouble(0x7FF_00000_7FFF_FFFFL), 0.0},
            {0.0, Double.longBitsToDouble(0x7FF0_7FFF_0000_FFFFL)},
            {Double.longBitsToDouble(0xFFF_00000_7FFF_FFFFL), 0.0},
            {0.0, Double.longBitsToDouble(0xFFF0_7FFF_0000_FFFFL)},
        };

        for (double[] exoticNaN: exoticNaNs) {
            failures += testAtan2Case(exoticNaN[0], exoticNaN[1],
                                      FdlibmTranslit.atan2(exoticNaN[0], exoticNaN[1]));
        }

        // Probe near decision points in the FDLIBM algorithm.
        double[][] decisionPoints = {
            // If x == 1, return atan(y)
            {0.5, Math.nextDown(1.0)},
            {0.5, 1.0},
            {0.5, Math.nextUp(1.0)},

            { MIN_VALUE,  MIN_VALUE},
            { MIN_VALUE, -MIN_VALUE},
            {-MIN_VALUE,  MIN_VALUE},
            {-MIN_VALUE, -MIN_VALUE},

            { MAX_VALUE,  MAX_VALUE},
            { MAX_VALUE, -MAX_VALUE},
            {-MAX_VALUE,  MAX_VALUE},
            {-MAX_VALUE, -MAX_VALUE},

            { MIN_VALUE,  MAX_VALUE},
            { MAX_VALUE,  MIN_VALUE},

            {-MIN_VALUE,  MAX_VALUE},
            {-MAX_VALUE,  MIN_VALUE},

            {MIN_VALUE,  -MAX_VALUE},
            {MAX_VALUE,  -MIN_VALUE},

            {-MIN_VALUE, -MAX_VALUE},
            {-MAX_VALUE, -MIN_VALUE},
        };

        for (double[] decisionPoint: decisionPoints) {
            failures += testAtan2Case(decisionPoint[0], decisionPoint[1],
                                      FdlibmTranslit.atan2(decisionPoint[0], decisionPoint[1]));
        }

        // atan2 looks at the ratio y/x and executes different code
        // paths accordingly: tests for 2^60 and 2^-60.

        double y = 1.0;
        double x = 0x1.0p60;
        double increment_x = Math.ulp(x);
        double increment_y = Math.ulp(y);
        y = y - 128*increment_y;
        x = x - 128*increment_x;

        for (int i = 0; i < 256; i++, x += increment_x) {
            for (int j = 0; j < 256; j++, y += increment_y) {
                failures += testAtan2Case( y,  x, FdlibmTranslit.atan2( y,  x));
                failures += testAtan2Case(-y,  x, FdlibmTranslit.atan2(-y,  x));
                failures += testAtan2Case( y, -x, FdlibmTranslit.atan2( y, -x));
                failures += testAtan2Case(-y, -x, FdlibmTranslit.atan2(-y, -x));

                failures += testAtan2Case( 2.0*y,  2.0*x, FdlibmTranslit.atan2( 2.0*y,  2.0*x));
                failures += testAtan2Case(-2.0*y,  2.0*x, FdlibmTranslit.atan2(-2.0*y,  2.0*x));
                failures += testAtan2Case( 2.0*y, -2.0*x, FdlibmTranslit.atan2( 2.0*y, -2.0*x));
                failures += testAtan2Case(-2.0*y, -2.0*x, FdlibmTranslit.atan2(-2.0*y, -2.0*x));

                failures += testAtan2Case( 0.5*y,  0.5*x, FdlibmTranslit.atan2( 0.5*y,  0.5*x));
                failures += testAtan2Case(-0.5*y,  0.5*x, FdlibmTranslit.atan2(-0.5*y,  0.5*x));
                failures += testAtan2Case( 0.5*y, -0.5*x, FdlibmTranslit.atan2( 0.5*y, -0.5*x));
                failures += testAtan2Case(-0.5*y, -0.5*x, FdlibmTranslit.atan2(-0.5*y, -0.5*x));

                // Switch argument position
                failures += testAtan2Case( x,  y, FdlibmTranslit.atan2( x,  y));
                failures += testAtan2Case(-x,  y, FdlibmTranslit.atan2(-x,  y));
                failures += testAtan2Case( x, -y, FdlibmTranslit.atan2( x, -y));
                failures += testAtan2Case(-x, -y, FdlibmTranslit.atan2(-x, -y));

                failures += testAtan2Case( 0.5*x,  0.5*y, FdlibmTranslit.atan2( 0.5*x,  0.5*y));
                failures += testAtan2Case(-0.5*x,  0.5*y, FdlibmTranslit.atan2(-0.5*x,  0.5*y));
                failures += testAtan2Case( 0.5*x, -0.5*y, FdlibmTranslit.atan2( 0.5*x, -0.5*y));
                failures += testAtan2Case(-0.5*x, -0.5*y, FdlibmTranslit.atan2(-0.5*x, -0.5*y));
            }
        }

        // Check random values
        for (int k = 0; k < 200; k++ ) {
            y = random.nextDouble();
            x = random.nextDouble();
            failures += testAtan2Case(y, x, FdlibmTranslit.atan2(y, x));
        }

        return failures;
    }

    private static int testAtan2Case(double input1, double input2, double expected) {
        int failures = 0;
        failures += Tests.test("StrictMath.atan2(double)", input1, input2,
                               StrictMath::atan2, expected);
        return failures;
    }
}
