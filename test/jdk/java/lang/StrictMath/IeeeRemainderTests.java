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
 * @bug 8304028
 * @key randomness
 * @summary Tests for StrictMath.IEEEremainder
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build Tests
 * @build FdlibmTranslit
 * @build IeeeRemainderTests
 * @run main IeeeRemainderTests
 */

import jdk.test.lib.RandomFactory;

/**
 * The tests in ../Math/IeeeRemainderTests.java test properties that
 * should hold for any IEEEremainder implementation, including the
 * FDLIBM-based one required for StrictMath.IEEEremainder.  Therefore,
 * the test cases in ../Math/IEEEremainderTests.java are run against
 * both the Math and StrictMath versions of IEEEremainder.  The role
 * of this test is to verify that the FDLIBM IEEEremainder algorithm
 * is being used by running golden file tests on values that may vary
 * from one conforming IEEEremainder implementation to another.
 */

public class IeeeRemainderTests {
    private IeeeRemainderTests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testAgainstTranslit();

        if (failures > 0) {
            System.err.println("Testing IEEEremainder incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    // Initialize shared random number generator
    private static java.util.Random random = RandomFactory.getRandom();

    /**
     * Test StrictMath.IEEEremainder against transliteration port of IEEEremainder.
     */
    private static int testAgainstTranslit() {
        int failures = 0;

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
            failures += testIEEEremainderCase(exoticNaN[0], exoticNaN[1],
                                              FdlibmTranslit.IEEEremainder(exoticNaN[0], exoticNaN[1]));
        }

        // Probe near decision points in the FDLIBM algorithm.
        double[][] decisionPoints = {
            {0x1.fffffp1022, 100.0},
            {0x1.fffffp1022, 0x1.fffffp1022},

            {2.0*0x1.0p-1022, 0x1.0p-1022},
            {2.0*0x1.0p-1022, 0x1.0p-1023},
        };


        for (var decisionPoint : decisionPoints) {
            double x = decisionPoint[0];
            double p = decisionPoint[1];
            double increment_x = Math.ulp(x);
            double increment_p = Math.ulp(p);

            x = x - 64*increment_x;
            p = p - 64*increment_p;

            for (int i = 0; i < 128; i++, x += increment_x) {
                for (int j = 0; j < 126; j++, p += increment_p) {
                    failures += testIEEEremainderCase( x,  p, FdlibmTranslit.IEEEremainder( x,  p));
                    failures += testIEEEremainderCase(-x,  p, FdlibmTranslit.IEEEremainder(-x,  p));
                    failures += testIEEEremainderCase( x, -p, FdlibmTranslit.IEEEremainder( x, -p));
                    failures += testIEEEremainderCase(-x, -p, FdlibmTranslit.IEEEremainder(-x, -p));
                }
            }
        }

        // Check random values
        for (int k = 0; k < 200; k++ ) {
            double x = random.nextDouble();
            double p = random.nextDouble();
            failures += testIEEEremainderCase(x, p, FdlibmTranslit.IEEEremainder(x, p));
        }

        return failures;
    }

    private static int testIEEEremainderCase(double input1, double input2, double expected) {
        int failures = 0;
        failures += Tests.test("StrictMath.IEEEremainder(double)", input1, input2,
                               StrictMath::IEEEremainder, expected);
        return failures;
    }
}
