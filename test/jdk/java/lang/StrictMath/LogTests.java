/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build Tests
 * @build FdlibmTranslit
 * @build LogTests
 * @run main LogTests
 * @summary Tests for StrictMath.log
 */

import jdk.test.lib.RandomFactory;

public class LogTests {
    private LogTests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testLog();
        failures += testAgainstTranslit();

        if (failures > 0) {
            System.err.println("Testing log incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    static int testLogCase(double input, double expected) {
        return Tests.test("StrictMath.log(double)", input,
                          StrictMath::log, expected);
    }

    // TODO: Find inputs where Math.log and StrictMath.log differ.
    static int testLog() {
        int failures = 0;

        double [][] testCases = {
        };

        for (double[] testCase: testCases)
            failures+=testLogCase(testCase[0], testCase[1]);

        return failures;
    }

    // Initialize shared random number generator
    private static java.util.Random random = RandomFactory.getRandom();

    /**
     * Test StrictMath.log against transliteration port of log.
     */
    private static int testAgainstTranslit() {
        int failures = 0;
        double x;

        // Test just above subnormal threshold...
        x = Double.MIN_NORMAL;
        failures += testRange(x, Math.ulp(x), 1000);

        // ... and just below subnormal threshold ...
        x = Math.nextDown(Double.MIN_NORMAL);
        failures += testRange(x, -Math.ulp(x), 1000);

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
            0x1.0p-1022,

            0x1.0p-20,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000);
        }

         x = Tests.createRandomDouble(random);

         // Make the increment twice the ulp value in case the random
         // value is near an exponent threshold. Don't worry about test
         // elements overflowing to infinity if the starting value is
         // near Double.MAX_VALUE.
         failures += testRange(x, 2.0 * Math.ulp(x), 1000);

         return failures;
    }

    private static int testRange(double start, double increment, int count) {
        int failures = 0;
        double x = start;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testLogCase(x, FdlibmTranslit.log(x));
        }
        return failures;
    }

    private static int testRangeMidpoint(double midpoint, double increment, int count) {
        int failures = 0;
        double x = midpoint - increment*(count / 2) ;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testLogCase(x, FdlibmTranslit.log(x));
        }
        return failures;
    }
}
