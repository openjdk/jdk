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
 * The tests in ../Math/InverseTripTests.java test properties that
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

        failures += testAgainstTranslitAsin();
        failures += testAgainstTranslitAcos();
        failures += testAgainstTranslitAtan();

        if (failures > 0) {
            System.err.println("Testing the inverse trig functions incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    // Initialize shared random number generator
    private static java.util.Random random = RandomFactory.getRandom();

    /**
     * Test StrictMath.asin against transliteration port of asin.
     */
    private static int testAgainstTranslitAsin() {
        int failures = 0;
        double x;

        // Test just above subnormal threshold...
        x = Double.MIN_NORMAL;
        failures += testRangeAsin(x, Math.ulp(x), 1000);

         // ... and just below subnormal threshold ...
          x = Math.nextDown(Double.MIN_NORMAL);
          failures += testRangeAsin(x, -Math.ulp(x), 1000);

         // ... and near 1.0 ...
          failures += testRangeMidpointAsin(1.0, Math.ulp(x), 2000);
          // (Note: probes every-other value less than 1.0 due to
          // change in the size of an ulp at 1.0.

          // Probe near decision points in the FDLIBM algorithm.
          double[] decisionPoints = {
               0.5,
              -0.5,

               0.975,
              -0.975,
          };

          for (double testPoint : decisionPoints) {
              failures += testRangeMidpointAsin(testPoint, Math.ulp(testPoint), 1000);
          }

          x = Tests.createRandomDouble(random);

          // Make the increment twice the ulp value in case the random
          // value is near an exponent threshold. Don't worry about test
          // elements overflowing to infinity if the starting value is
          // near Double.MAX_VALUE.
          failures += testRangeAsin(x, 2.0 * Math.ulp(x), 1000);

         return failures;
    }

    /**
     * Test StrictMath.acos against transliteration port of acos.
     */
    private static int testAgainstTranslitAcos() {
        int failures = 0;
        double x;

        // Test just above subnormal threshold...
        x = Double.MIN_NORMAL;
        failures += testRangeAcos(x, Math.ulp(x), 1000);

         // ... and just below subnormal threshold ...
          x = Math.nextDown(Double.MIN_NORMAL);
          failures += testRangeAcos(x, -Math.ulp(x), 1000);

         // ... and near 1.0 ...
          failures += testRangeMidpointAcos(1.0, Math.ulp(x), 2000);
          // (Note: probes every-other value less than 1.0 due to
          // change in the size of an ulp at 1.0.

          // Probe near decision points in the FDLIBM algorithm.
          double[] decisionPoints = {
               0.5,
              -0.5,

               0x1.0p-57,
              -0x1.0p-57,
          };

          for (double testPoint : decisionPoints) {
              failures += testRangeMidpointAcos(testPoint, Math.ulp(testPoint), 1000);
          }

          x = Tests.createRandomDouble(random);

          // Make the increment twice the ulp value in case the random
          // value is near an exponent threshold. Don't worry about test
          // elements overflowing to infinity if the starting value is
          // near Double.MAX_VALUE.
          failures += testRangeAcos(x, 2.0 * Math.ulp(x), 1000);

         return failures;
    }

    /**
     * Test StrictMath.atan against transliteration port of atan
     */
    private static int testAgainstTranslitAtan() {
        int failures = 0;
        double x;

        // Test just above subnormal threshold...
        x = Double.MIN_NORMAL;
        failures += testRangeAtan(x, Math.ulp(x), 1000);

         // ... and just below subnormal threshold ...
          x = Math.nextDown(Double.MIN_NORMAL);
          failures += testRangeAtan(x, -Math.ulp(x), 1000);

         // ... and near 1.0 ...
          failures += testRangeMidpointAtan(1.0, Math.ulp(x), 2000);
          // (Note: probes every-other value less than 1.0 due to
          // change in the size of an ulp at 1.0.

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
              failures += testRangeMidpointAtan(testPoint, Math.ulp(testPoint), 1000);
          }

          x = Tests.createRandomDouble(random);

          // Make the increment twice the ulp value in case the random
          // value is near an exponent threshold. Don't worry about test
          // elements overflowing to infinity if the starting value is
          // near Double.MAX_VALUE.
          failures += testRangeAtan(x, 2.0 * Math.ulp(x), 1000);

         return failures;
    }

    private static int testRangeAsin(double start, double increment, int count) {
        int failures = 0;
        double x = start;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testAsinCase(x, FdlibmTranslit.asin(x));
        }
        return failures;
    }

    private static int testRangeAcos(double start, double increment, int count) {
        int failures = 0;
        double x = start;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testAcosCase(x, FdlibmTranslit.acos(x));
        }
        return failures;
    }

    private static int testRangeAtan(double start, double increment, int count) {
        int failures = 0;
        double x = start;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testAtanCase(x, FdlibmTranslit.atan(x));
        }
        return failures;
    }

    private static int testRangeMidpointAsin(double midpoint, double increment, int count) {
        int failures = 0;
        double x = midpoint - increment*(count / 2) ;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testAsinCase(x, FdlibmTranslit.asin(x));
        }
        return failures;
    }

    private static int testRangeMidpointAcos(double midpoint, double increment, int count) {
        int failures = 0;
        double x = midpoint - increment*(count / 2) ;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testAcosCase(x, FdlibmTranslit.acos(x));
        }
        return failures;
    }

    private static int testRangeMidpointAtan(double midpoint, double increment, int count) {
        int failures = 0;
        double x = midpoint - increment*(count / 2) ;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testAtanCase(x, FdlibmTranslit.atan(x));
        }
        return failures;
    }

    static int testAsinCase(double input, double expected) {
        return Tests.test("StrictMath.asin(double)", input,
                          StrictMath::asin, expected);
    }

    static int testAcosCase(double input, double expected) {
        return Tests.test("StrictMath.acos(double)", input,
                          StrictMath::acos, expected);
    }

    static int testAtanCase(double input, double expected) {
        return Tests.test("StrictMath.atan(double)", input,
                          StrictMath::atan, expected);
    }
}
