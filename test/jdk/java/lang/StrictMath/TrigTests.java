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
import jdk.test.lib.RandomFactory;
import java.util.function.DoubleUnaryOperator;

/*
 * @test
 * @bug 8302027
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build Tests
 * @build FdlibmTranslit
 * @build TrigTests
 * @run main TrigTests
 * @summary Tests for StrictMath.{sin, cos, tan}
 */

/**
 * The tests in ../Math/{TanTests.java, SinCosTests.java} test
 * properties that should hold for any implementation of the trig
 * functions sin, cos, and tan, including the FDLIBM-based ones
 * required by the StrictMath class.  Therefore, the test cases in
 * ../Math/{TanTests.java, SinCosTests.java} are run against both the
 * Math and StrictMath versions of the trig methods.  The role of this
 * test is to verify that the FDLIBM algorithms are being used by
 * running golden file tests on values that may vary from one
 * conforming implementation of the trig functions to another.
 */

public class TrigTests {
    private TrigTests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testAgainstTranslitCommon();

        failures += testAgainstTranslitSin();
        failures += testAgainstTranslitCos();
        failures += testAgainstTranslitTan();

        if (failures > 0) {
            System.err.println("Testing the trig functions incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    /**
     * Bundle together groups of testing methods.
     */
    private static enum TrigTest {
        SIN(TrigTests::testSinCase, FdlibmTranslit::sin),
        COS(TrigTests::testCosCase, FdlibmTranslit::cos),
        TAN(TrigTests::testTanCase, FdlibmTranslit::tan);

        private DoubleDoubleToInt testCase;
        private DoubleUnaryOperator transliteration;

        TrigTest(DoubleDoubleToInt testCase, DoubleUnaryOperator transliteration) {
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
             Math.PI/4.0,
            -Math.PI/4.0,

             Math.PI/2.0,
            -Math.PI/2.0,

             3.0*Math.PI/2.0,
            -3.0*Math.PI/2.0,

             Math.PI,
            -Math.PI,

             2.0*Math.PI,
            -2.0*Math.PI,

             Double.MIN_NORMAL,
             1.0,
             Tests.createRandomDouble(random),
        };

        for (var testMethods : TrigTest.values()) {
            for (double testPoint : pointsOfInterest) {
                failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, testMethods);
            }
        }

        return failures;
    }

    /**
     * Test StrictMath.sin against transliteration port of sin.
     */
    private static int testAgainstTranslitSin() {
        int failures = 0;

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
             0x1.0p-27,
            -0x1.0p-27,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, TrigTest.SIN);
        }

        return failures;
    }

    /**
     * Test StrictMath.cos against transliteration port of cos.
     */
    private static int testAgainstTranslitCos() {
        int failures = 0;

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
             0x1.0p27,
            -0x1.0p27,

             0.78125,
            -0.78125,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, TrigTest.COS);
        }

        return failures;
    }

    /**
     * Test StrictMath.tan against transliteration port of tan
     */
    private static int testAgainstTranslitTan() {
        int failures = 0;

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
             0x1.0p-28,
            -0x1.0p-28,

             0.6744,
            -0.6744,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000,  TrigTest.TAN);
        }

        return failures;
    }

    private interface DoubleDoubleToInt {
        int apply(double x, double y);
    }

    private static int testRange(double start, double increment, int count,
                             TrigTest testMethods) {
        int failures = 0;
        double x = start;
        for (int i = 0; i < count; i++, x += increment) {
            failures +=
                testMethods.testCase().apply(x, testMethods.transliteration().applyAsDouble(x));
        }
        return failures;
    }

    private static int testRangeMidpoint(double midpoint, double increment, int count,
                                         TrigTest testMethods) {
        int failures = 0;
        double x = midpoint - increment*(count / 2) ;
        for (int i = 0; i < count; i++, x += increment) {
            failures +=
                testMethods.testCase().apply(x, testMethods.transliteration().applyAsDouble(x));
        }
        return failures;
    }

    private static int testSinCase(double input, double expected) {
        return Tests.test("StrictMath.sin(double)", input,
                          StrictMath::sin, expected);
    }

    private static int testCosCase(double input, double expected) {
        return Tests.test("StrictMath.cos(double)", input,
                          StrictMath::cos, expected);
    }

    private static int testTanCase(double input, double expected) {
        return Tests.test("StrictMath.tan(double)", input,
                          StrictMath::tan, expected);
    }
}
