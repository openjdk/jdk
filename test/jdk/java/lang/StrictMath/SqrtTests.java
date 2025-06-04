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
 * @bug 8302040
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build Tests
 * @build FdlibmTranslit
 * @build SqrtTests
 * @run main SqrtTests
 * @summary Tests for StrictMath.sqrt
 */
import jdk.test.lib.RandomFactory;

/**
 * The tests in ../Math/SqrtTests.java test properties that should
 * hold for any sqrt implementation, including the FDLIBM-based one
 * required for StrictMath.sqrt.  Therefore, the test cases in
 * ../Math/SqrtTests.java are run against both the Math and
 * StrictMath versions of sqrt.  The role of this test is to verify
 * that the FDLIBM sqrt algorithm is being used by running golden
 * file tests on values that may vary from one conforming sqrt
 * implementation to another.
 */

public class SqrtTests {
    private SqrtTests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testAgainstTranslit();

        if (failures > 0) {
            System.err.println("Testing sqrt incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    // Initialize shared random number generator
    private static java.util.Random random = RandomFactory.getRandom();

    /**
     * Test StrictMath.sqrt against transliteration port of sqrt.
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

        // ... and near 1.0 ...
        failures += testRangeMidpoint(1.0, Math.ulp(x), 2000);
        // (Note: probes every-other value less than 1.0 due to
        // change in the size of an ulp at 1.0.

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
            Double.MIN_VALUE,
            Double.MAX_VALUE,
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
            failures += testSqrtCase(x, FdlibmTranslit.sqrt(x));
        }
        return failures;
    }

    private static int testRangeMidpoint(double midpoint, double increment, int count) {
        int failures = 0;
        double x = midpoint - increment*(count / 2) ;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testSqrtCase(x, FdlibmTranslit.sqrt(x));
        }
        return failures;
    }

    private static int testSqrtCase(double input, double expected) {
        return Tests.test("StrictMath.sqrt(double)", input,
                          StrictMath::sqrt, expected);
    }
}
