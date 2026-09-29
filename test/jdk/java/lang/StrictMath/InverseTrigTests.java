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
import jdk.test.lib.RandomFactory;
import java.util.function.DoubleUnaryOperator;

/*
 * @test
 * @bug 8302026
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build Tests
 * @build FdlibmTranslit
 * @build InverseTrigTests
 * @run main InverseTrigTests
 * @summary Tests for StrictMath.{asin, acos, atan}
 */

/**
 * The tests in ../Math/InverseTrigTests.java test properties that
 * should hold for any implementation of the inverse trig functions
 * ason, acos, and atan, including the FDLIBM-based ones required by
 * the StrictMath class.  Therefore, the test cases in
 * ../Math/InverseTrig.java are run against both the Math and
 * StrictMath versions of the inverse trig methods.  The role of this
 * test is to verify that the FDLIBM algorithms are being used by
 * running golden file tests on values that may vary from one
 * conforming implementation of the hyperbolics to another.
 */

public class InverseTrigTests {
    private InverseTrigTests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testAgainstTranslitCommon();

        failures += testAsin();
        failures += testAcos();
        failures += testAtan();

        if (failures > 0) {
            System.err.println("Testing the inverse trig functions incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    /**
     * Bundle together groups of testing methods.
     */
    private static enum InverseTrigTest {
        ASIN(InverseTrigTests::testAsinCase, FdlibmTranslit::asin),
        ACOS(InverseTrigTests::testAcosCase, FdlibmTranslit::acos),
        ATAN(InverseTrigTests::testAtanCase, FdlibmTranslit::atan);

        private DoubleDoubleToInt testCase;
        private DoubleUnaryOperator transliteration;

        InverseTrigTest(DoubleDoubleToInt testCase, DoubleUnaryOperator transliteration) {
            this.testCase = testCase;
            this.transliteration = transliteration;
        }

        public DoubleDoubleToInt testCase() {return testCase;}
        public DoubleUnaryOperator transliteration() {return transliteration;}
    }

    // Initialize shared random number generator
    private static java.util.Random random = RandomFactory.getRandom();

    /**
     * Test against shared points of interest.
     */
    private static int testAgainstTranslitCommon() {
        int failures = 0;
        double[] pointsOfInterest = {
            Double.MIN_NORMAL,
            1.0,
            Tests.createRandomDouble(random),
        };

        for (var testMethods : InverseTrigTest.values()) {
            for (double testPoint : pointsOfInterest) {
                failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, testMethods);
            }
        }

        return failures;
    }

    /**
     * Test StrictMath.asin against transliteration port of asin.
     */
    private static int testAsin() {
        int failures = 0;

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
             0x1p-27,
            -0x1p-27,

             0.5,
            -0.5,

             0.975,
            -0.975,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, InverseTrigTest.ASIN);
        }

        // Empirical worst-case points in other libraries with larger
        // worst-case error than FDLIBM
        double [][] testCases = {
            {-0x1.00d44cccfa99p-1,  -0x1.0d0a6a0e79e15p-1},
            {-0x1.0239000439deep-1, -0x1.0ea71ea2a7cd7p-1},
            {+0x1.0479b37d95e5cp-1,  0x1.1143fafdc5b2cp-1},
            {-0x1.2ef2481799c7cp-1, -0x1.442d10aa50906p-1},
        };
        for (double[] testCase : testCases) {
            failures += testAsinCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    /**
     * Test StrictMath.acos against transliteration port of acos.
     */
    private static int testAcos() {
        int failures = 0;

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
             0.5,
            -0.5,

             0x1.0p-57,
            -0x1.0p-57,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, InverseTrigTest.ACOS);
        }

        // Empirical worst-case points in other libraries with larger
        // worst-case error than FDLIBM
        double [][] testCases = {
            {+0x1.35b03e336a82bp-1,  0x1.d7a84ec2f6707p-1},
            {-0x1.8d313198a2e03p-53, 0x1.921fb54442d19p0},
            {-0x1.010fd0ad6aa41p-1,  0x1.0c63a8cd23befp1},
            {+0x1.251869c3f7881p-1,  0x1.ec2ff0c102684p-1},
            {+0x1.266637a3d2bbcp-1,  0x1.ea98637533648p-1},
        };
        for (double[] testCase : testCases) {
            failures += testAcosCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    /**
     * Test StrictMath.atan against transliteration port of atan
     */
    private static int testAtan() {
        int failures = 0;

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
            0.0,

            7.0/16.0,
            11.0/16.0,
            19.0/16.0,
            39.0/16.0,

            0x1.0p66,
            0x1.0p-29,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, InverseTrigTest.ATAN);
        }

        // Empirical worst-case points in other libraries with larger
        // worst-case error than FDLIBM
        double [][] testCases = {
            {+0x1.0103fc4ebaaa8p+1,  0x1.1bd5c375c97b5p0},
            {+0x1.01e0be37af68fp+1,  0x1.1c2d45ec82765p0},
            {-0x1.60370d15396b7p-1, -0x1.348461c347fd9p-1},
            {+0x1.032b4811f3dc5p+0,  0x1.9545fd233ee14p-1},
            {+0x1.52184b1b9bd9bp+0,  0x1.d86de33c93814p-1},
            {-0x1.0684fa9fa7481p+0, -0x1.988f9da70b11ap-1},
        };
        for (double[] testCase : testCases) {
            failures += testAtanCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private interface DoubleDoubleToInt {
        int apply(double x, double y);
    }

    private static int testRange(double start, double increment, int count,
                             InverseTrigTest testMethods) {
        int failures = 0;
        double x = start;
        for (int i = 0; i < count; i++, x += increment) {
            failures +=
                testMethods.testCase().apply(x, testMethods.transliteration().applyAsDouble(x));
        }
        return failures;
    }

    private static int testRangeMidpoint(double midpoint, double increment, int count,
                                         InverseTrigTest testMethods) {
        int failures = 0;
        double x = midpoint - increment*(count / 2) ;
        for (int i = 0; i < count; i++, x += increment) {
            failures +=
                testMethods.testCase().apply(x, testMethods.transliteration().applyAsDouble(x));
        }
        return failures;
    }

    private static int testAsinCase(double input, double expected) {
        return Tests.test("StrictMath.asin(double)", input,
                          StrictMath::asin, expected);
    }

    private static int testAcosCase(double input, double expected) {
        return Tests.test("StrictMath.acos(double)", input,
                          StrictMath::acos, expected);
    }

    private static int testAtanCase(double input, double expected) {
        return Tests.test("StrictMath.atan(double)", input,
                          StrictMath::atan, expected);
    }
}
