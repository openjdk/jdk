/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4851625 8301444 8331354
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build Tests
 * @build FdlibmTranslit
 * @build HyperbolicTests
 * @run main HyperbolicTests
 * @summary Tests for StrictMath.{sinh, cosh, tanh, asinh, acosh}
 */

/**
 * The tests in ../Math/HyperbolicTests.java test properties that
 * should hold for any implementation of the hyperbolic functions
 * sinh, cosh, and tanh, including the FDLIBM-based ones required by
 * the StrictMath class.  Therefore, the test cases in
 * ../Math/HyperbolicTests.java are run against both the Math and
 * StrictMath versions of the hyperbolic methods.  The role of this
 * test is to verify that the FDLIBM algorithms are being used by
 * running golden file tests on values that may vary from one
 * conforming implementation of the hyperbolics to another.
 */

public class HyperbolicTests {
    private HyperbolicTests(){}

    public static void main(String... args) {
        int failures = 0;

        failures += testAgainstTranslitCommon();

        failures += testAgainstTranslitSinh();
        failures += testAgainstTranslitCosh();
        failures += testAgainstTranslitTanh();

        failures += testSinh();
        failures += testCosh();
        failures += testTanh();
        failures += testAsinh();
        failures += testAcosh();

        if (failures > 0) {
            System.err.println("Testing the hyperbolics incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    /**
     * Bundle together groups of testing methods.
     */
    private static enum HyperbolicTest {
        SINH(HyperbolicTests::testSinhCase, FdlibmTranslit::sinh),
        COSH(HyperbolicTests::testCoshCase, FdlibmTranslit::cosh),
        TANH(HyperbolicTests::testTanhCase, FdlibmTranslit::tanh),
        ASINH(HyperbolicTests::testAsinhCase, FdlibmTranslit::asinh),
        ACOSH(HyperbolicTests::testAcoshCase, FdlibmTranslit::acosh);

        private DoubleDoubleToInt testCase;
        private DoubleUnaryOperator transliteration;

        HyperbolicTest(DoubleDoubleToInt testCase, DoubleUnaryOperator transliteration) {
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

        for (var testMethods : HyperbolicTest.values()) {
            for (double testPoint : pointsOfInterest) {
                failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, testMethods);
            }
        }

        return failures;
    }

    /**
     * Test StrictMath.sinh against transliteration port of sinh.
     */
    private static int testAgainstTranslitSinh() {
        int failures = 0;
        double x;

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
            0.0,

             22.0,
            -22.0,

             0x1.0p-28,
            -0x1.0p-28,

            // StrictMath.log(Double.MAX_VALUE) ~= 709.782712893384
             0x1.62e42fefa39efp9,
            -0x1.62e42fefa39efp9,

            // Largest argument with finite sinh, 710.4758600739439
             0x1.633ce8fb9f87dp9,
            -0x1.633ce8fb9f87dp9,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, HyperbolicTest.SINH);
        }

        return failures;
    }

    /**
     * Test StrictMath.cosh against transliteration port of cosh.
     */
    private static int testAgainstTranslitCosh() {
        int failures = 0;
        double x;

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
            0.0,

             22.0,
            -22.0,

            // StrictMath.log(2)/2 ~= 0.34657359027997264
             0x1.62e42fefa39efp-2,
            -0x1.62e42fefa39efp-2,

             0x1.0p-28,
            -0x1.0p-28,

            // StrictMath.log(Double.MAX_VALUE) ~= 709.782712893384
             0x1.62e42fefa39efp9,
            -0x1.62e42fefa39efp9,

            // Largest argument with finite cosh, 710.4758600739439
             0x1.633ce8fb9f87dp9,
            -0x1.633ce8fb9f87dp9,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, HyperbolicTest.COSH);
        }

        return failures;
    }

    /**
     * Test StrictMath.tanh against transliteration port of tanh
     */
    private static int testAgainstTranslitTanh() {
        int failures = 0;
        double x;

        // Probe near decision points in the FDLIBM algorithm.
        double[] decisionPoints = {
             0.0,

             0x1.0p-55,
            -0x1.0p-55,

             1.0,
            -1.0,

             22.0,
        };

        for (double testPoint : decisionPoints) {
            failures += testRangeMidpoint(testPoint, Math.ulp(testPoint), 1000, HyperbolicTest.COSH);
        }

        return failures;
    }

    private interface DoubleDoubleToInt {
        int apply(double x, double y);
    }

    private static int testRange(double start, double increment, int count,
                                 HyperbolicTest testMethods) {
        int failures = 0;
        double x = start;
        for (int i = 0; i < count; i++, x += increment) {
            failures +=
                testMethods.testCase().apply(x, testMethods.transliteration().applyAsDouble(x));
        }
        return failures;
    }

    private static int testRangeMidpoint(double midpoint, double increment, int count,
                                         HyperbolicTest testMethods) {
        int failures = 0;
        double x = midpoint - increment*(count / 2) ;
        for (int i = 0; i < count; i++, x += increment) {
            failures +=
                testMethods.testCase().apply(x, testMethods.transliteration().applyAsDouble(x));
        }
        return failures;
    }

    private static int testSinhCase(double input, double expected) {
        return Tests.test("StrictMath.sinh(double)", input,
                          StrictMath::sinh, expected);
    }

    private static int testCoshCase(double input, double expected) {
        return Tests.test("StrictMath.cosh(double)", input,
                          StrictMath::cosh, expected);
    }

    private static int testTanhCase(double input, double expected) {
        return Tests.test("StrictMath.tanh(double)", input,
                          StrictMath::tanh, expected);
    }

    private static int testAsinhCase(double input, double expected) {
        return Tests.test("StrictMath.asinh(double)", input,
                StrictMath::asinh, expected);
    }

    private static int testAcoshCase(double input, double expected) {
        return Tests.test("StrictMath.asinh(double)", input,
                StrictMath::acosh, expected);
    }

    private static int testSinh() {
        int failures = 0;
        double [][] testCases = {
            {0x1.5798ee2308c3ap-27,     0x1.5798ee2308c3bp-27},
            {0x1.ffffffffffff8p-26,     0x1.ffffffffffffap-26},
            {0x1.ffffffffffffep-26,     0x1.0p-25},
            {0x1.ffffffffffff8p-25,     0x1.ffffffffffffep-25},
            {0x1.ffffffffffffap-25,     0x1.0p-24},
            {0x1.ad7f29abcaf47p-24,     0x1.ad7f29abcaf53p-24},
            {0x1.ad7f29abcaf48p-24,     0x1.ad7f29abcaf54p-24},
            {0x1.fffffffffffeap-24,     0x1.0p-23},
            {0x1.ffffffffffff8p-24,     0x1.0000000000007p-23},
            {0x1.fffffffffffaap-23,     0x1.0p-22},
            {0x1.ffffffffffff8p-23,     0x1.0000000000027p-22},
            {0x1.ffffffffffeaap-22,     0x1.0p-21},
            {0x1.ffffffffffff8p-22,     0x1.00000000000a7p-21},
            {0x1.ffffffffffaaap-21,     0x1.0p-20},
            {0x1.ffffffffffff8p-21,     0x1.00000000002a7p-20},
            {0x1.0c6f7a0b5ed8cp-20,     0x1.0c6f7a0b5f09fp-20},
            {0x1.0c6f7a0b5ed8dp-20,     0x1.0c6f7a0b5f0ap-20},
            {0x1.fffffffffeaaap-20,     0x1.0p-19},
            {0x1.ffffffffffff8p-20,     0x1.0000000000aa7p-19},
            {0x1.ffffffffffff8p-19,     0x1.0000000002aa7p-18},
            {0x1.ffffffffffff7p-18,     0x1.000000000aaa6p-17},
            {0x1.4f8b588e368d9p-17,     0x1.4f8b588e4e928p-17},
            {0x1.ffffffffffffep-17,     0x1.000000002aaa9p-16},
            {0x1.0p-16,                 0x1.000000002aaaap-16},
            {0x1.fffffffffffffp-16,     0x1.00000000aaaabp-15},
            {0x1.fffffffffeaaap-15,     0x1.00000002aap-14},
            {0x1.ffffffffffffep-15,     0x1.00000002aaaa9p-14},
            {0x1.0p-14,                 0x1.00000002aaaaap-14},
            {0x1.a36e2eb1c3dd4p-14,     0x1.a36e2ebd7e43ap-14},
            {0x1.a36e2eb1c3f8cp-14,     0x1.a36e2ebd7e5f1p-14},
            {0x1.a36e2eb1c432cp-14,     0x1.a36e2ebd7e991p-14},
            {0x1.fffffffffffffp-14,     0x1.0000000aaaaabp-13},
            {0x1.ffffffffffffep-13,     0x1.0000002aaaaa9p-12},
            {0x1.0p-12,                 0x1.0000002aaaaaap-12},
            {0x1.ffffffffff7f9p-12,     0x1.000000aaaa6a9p-11},
            {0x1.fffffffffffffp-12,     0x1.000000aaaaaadp-11},
            {0x1.ffffffffffffep-11,     0x1.000002aaaaacbp-10},
            {0x1.0p-10,                 0x1.000002aaaaaccp-10},
            {0x1.0624dd2f1a79p-10,      0x1.0624e00c1c776p-10},
            {0x1.0624dd2f1a8c9p-10,     0x1.0624e00c1c8bp-10},
            {0x1.0624dd2f1a9fcp-10,     0x1.0624e00c1c9e3p-10},
            {0x1.ffffffffffffep-10,     0x1.00000aaaaaccbp-9},
            {0x1.0p-9,                  0x1.00000aaaaacccp-9},
            {0x1.ffffffffffe4ap-9,      0x1.00002aaaacbf2p-8},
            {0x1.fffffffffffffp-9,      0x1.00002aaaacccdp-8},
            {0x1.fffffffffff9dp-8,      0x1.0000aaaaccc9bp-7},
            {0x1.ffffffffffffep-8,      0x1.0000aaaacccccp-7},
            {0x1.0p-7,                  0x1.0000aaaaccccdp-7},
            {0x1.47ae147ae146fp-7,      0x1.47af7a654e9e2p-7},
            {0x1.47ae147ae147ap-7,      0x1.47af7a654e9eep-7},
            {0x1.47ae147ae147bp-7,      0x1.47af7a654e9efp-7},
            {0x1.fffffffffffb6p-7,      0x1.0002aaaccccb4p-6},
            {0x1.fffffffffffcap-7,      0x1.0002aaaccccbep-6},
            {0x1.ffffffffffff7p-7,      0x1.0002aaaccccd5p-6},
            {0x1.fffffffffffe9p-6,      0x1.000aaacccd001p-5},
            {0x1.ffffffffffff7p-6,      0x1.000aaacccd008p-5},
            {0x1.fffffffffffffp-6,      0x1.000aaacccd00dp-5},
            {0x1.ffffffffffff6p-5,      0x1.002aacccd9cd7p-4},
            {0x1.ffffffffffff8p-5,      0x1.002aacccd9cd9p-4},
            {0x1.0p-4,                  0x1.002aacccd9cddp-4},
            {0x1.9999999999995p-4,      0x1.9a487337b59afp-4},
            {0x1.9999999999996p-4,      0x1.9a487337b59afp-4},
            {0x1.9999999999998p-4,      0x1.9a487337b59b1p-4},
            {0x1.ffffffffffffap-4,      0x1.00aaccd00d2edp-3},
            {0x1.ffffffffffffcp-4,      0x1.00aaccd00d2efp-3},
            {0x1.ffffffffffff3p-3,      0x1.02accd9d080fbp-2},
            {0x1.ffffffffffffdp-3,      0x1.02accd9d08101p-2},
            {0x1.fffffffffffffp-3,      0x1.02accd9d08101p-2},
            {0x1.fffffffffffecp-2,      0x1.0acd00fe63b8cp-1},
            {0x1.ffffffffffffcp-2,      0x1.0acd00fe63b94p-1},
            {0x1.0p-1,                  0x1.0acd00fe63b97p-1},
            {0x1.ffffffffffff6p-1,      0x1.2cd9fc44eb97ap0},
            {0x1.ffffffffffffep-1,      0x1.2cd9fc44eb981p0},
            {0x1.fffffffffffffp0,       0x1.d03cf63b6e19ep1},
            {0x1.0p1,                   0x1.d03cf63b6e1ap1},
            {0x1.fffffffffffffp1,       0x1.b4a380370362dp4},
            {0x1.0p2,                   0x1.b4a380370363p4},
            {0x1.ffffffffffffcp2,       0x1.749ea514eca4ep10},
            {0x1.0p3,                   0x1.749ea514eca66p10},
            {0x1.fffffffffffffp3,       0x1.0f2ebd0a7ffdcp22},
            {0x1.0p4,                   0x1.0f2ebd0a7ffe4p22},
            {0x1.fffffffffff68p4,       0x1.1f43fcc4b5b83p45},
            {0x1.fffffffffffd4p4,       0x1.1f43fcc4b6316p45},
            {0x1.0p5,                   0x1.1f43fcc4b662cp45},

             // Julia worst-case input
            {-0x1.633c654fee2bap9,      -0x1.fdf25fc26e7cp1023},

            // Empirical worst-case points in other libraries with
            // larger worst-case errors than FDLIBM
            {-0x1.633c654fee2bap+9,    -0x1.fdf25fc26e7cp1023},
            {-0x1.633cae1335f26p+9,    -0x1.ff149489e50a1p1023},
            { 0x1.9fcba01feb507p-2,     0x1.ab50d8e4d8c56p-2},
        };

        for (double[] testCase: testCases)
            failures += testSinhCase(testCase[0], testCase[1]);

        return failures;
    }

    private static int testCosh() {
        int failures = 0;
        double [][] testCases = {
            {0x1.fffffffffb49fp-8,      0x1.00020000aaaabp0},
            {0x1.47ae147ae0e45p-7,      0x1.000346de27853p0},
            {0x1.fffffffffd9f3p-7,      0x1.0008000aaab05p0},
            {0x1.ffffffffff9f1p-7,      0x1.0008000aaab05p0},
            {0x1.fffffffffe27dp-6,      0x1.002000aaac169p0},
            {0x1.ffffffffff27bp-6,      0x1.002000aaac16bp0},
            {0x1.ffffffffffb9cp-5,      0x1.00800aab05b1ep0},
            {0x1.ffffffffffd9dp-5,      0x1.00800aab05b1fp0},
            {0x1.9999999999368p-4,      0x1.0147f40224b2ep0},
            {0x1.9999999999727p-4,      0x1.0147f40224b35p0},
            {0x1.ffffffffffed1p-4,      0x1.0200aac16db6cp0},
            {0x1.fffffffffffd1p-4,      0x1.0200aac16db6ep0},
            {0x1.ffffffffffeb4p-3,      0x1.080ab05ca613bp0},
            {0x1.ffffffffffff2p-3,      0x1.080ab05ca6146p0},
            {0x1.ffffffffffff3p-2,      0x1.20ac1862ae8cep0},
            {0x1.ffffffffffff9p-2,      0x1.20ac1862ae8dp0},
            {0x1.0p0,                   0x1.8b07551d9f551p0},
            {0x1.ffffffffffffbp0,       0x1.e18fa0df2d9b3p1},
            {0x1.ffffffffffffep0,       0x1.e18fa0df2d9b8p1},
            {0x1.fffffffffffffp0,       0x1.e18fa0df2d9bap1},
            {0x1.ffffffffffff9p1,       0x1.b4ee858de3e68p4},
            {0x1.ffffffffffffep1,       0x1.b4ee858de3e7ap4},
            {0x1.fffffffffffffp1,       0x1.b4ee858de3e7dp4},
            {0x1.ffffffffffffcp2,       0x1.749eaa93f4e5ep10},
            {0x1.ffffffffffffdp2,       0x1.749eaa93f4e64p10},
            {0x1.0p3,                   0x1.749eaa93f4e76p10},
            {0x1.fffffffffff6fp3,       0x1.0f2ebd0a7fb9p22},
            {0x1.0p4,                   0x1.0f2ebd0a8005cp22},
            {0x1.fffffffffffd4p4,       0x1.1f43fcc4b6316p45},
            {0x1.0p5,                   0x1.1f43fcc4b662cp45},

             // Julia worst-case input
            {-0x1.633c654fee2bap9,      0x1.fdf25fc26e7cp1023},

            // Empirical worst-case points in other libraries with
            // larger worst-case errors than FDLIBM
            {-0x1.633c654fee2bap+9,     0x1.fdf25fc26e7cp1023},
            { 0x1.ff76fb3f476d5p+0,     0x1.e0976c8f0ebdfp1},
            { 0x1.633cc2ae1c934p+9,     0x1.ff66e0de4dc6fp1023},
            {-0x1.1ff088806d82ep+3,     0x1.f97ccb0aef314p11},
            {-0x1.628af341989dap+9,     0x1.fdf28623ef923p1021},
        };

        for (double[] testCase: testCases)
            failures += testCoshCase(testCase[0], testCase[1]);

        return failures;
    }

    private static int testTanh() {
        int failures = 0;
        double [][] testCases = {
            {0x1.5798ee2308c36p-27,     0x1.5798ee2308c36p-27},
            {0x1.ffffffffffffep-26,     0x1.ffffffffffffbp-26},
            {0x1.ffffffffffffep-25,     0x1.ffffffffffff3p-25},
            {0x1.ad7f29abcaf47p-24,     0x1.ad7f29abcaf2dp-24},
            {0x1.ad7f29abcaf48p-24,     0x1.ad7f29abcaf2ep-24},
            {0x1.ffffffffffffep-24,     0x1.fffffffffffd3p-24},
            {0x1.ffffffffffffep-23,     0x1.fffffffffff53p-23},
            {0x1.ffffffffffffep-22,     0x1.ffffffffffd53p-22},
            {0x1.ffffffffffffep-21,     0x1.ffffffffff553p-21},
            {0x1.0c6f7a0b5ed8dp-20,     0x1.0c6f7a0b5e767p-20},
            {0x1.ffffffffffffep-20,     0x1.fffffffffd553p-20},
            {0x1.ffffffffffffep-19,     0x1.fffffffff5553p-19},
            {0x1.fffffffffffffp-18,     0x1.ffffffffd5555p-18},
            {0x1.0p-17,                 0x1.ffffffffd5556p-18},
            {0x1.4f8b588e368edp-17,     0x1.4f8b588e0685p-17},
            {0x1.fffffffffffffp-17,     0x1.ffffffff55554p-17},
            {0x1.fffffffffffffp-16,     0x1.fffffffd55555p-16},
            {0x1.0p-15,                 0x1.fffffffd55556p-16},
            {0x1.fffffffffe5ddp-15,     0x1.fffffff553b33p-15},
            {0x1.fffffffffffffp-15,     0x1.fffffff555554p-15},
            {0x1.a36e2eb1c432dp-14,     0x1.a36e2e9a4f663p-14},
            {0x1.ffffffffffffep-14,     0x1.ffffffd555553p-14},
            {0x1.0p-13,                 0x1.ffffffd555555p-14},
            {0x1.ffffffffffd51p-13,     0x1.ffffff55552aap-13},
            {0x1.fffffffffffffp-13,     0x1.ffffff5555559p-13},
            {0x1.ffffffffffffep-12,     0x1.fffffd5555597p-12},
            {0x1.0p-11,                 0x1.fffffd5555599p-12},
            {0x1.fffffffffff1p-11,      0x1.fffff555558a9p-11},
            {0x1.0p-10,                 0x1.fffff5555599ap-11},
            {0x1.0624dd2f1a9c6p-10,     0x1.0624d77516cabp-10},
            {0x1.0624dd2f1a9f8p-10,     0x1.0624d77516cdep-10},
            {0x1.fffffffffffddp-10,     0x1.ffffd55559976p-10},
            {0x1.fffffffffffffp-10,     0x1.ffffd55559999p-10},
            {0x1.ffffffffffffcp-9,      0x1.ffff555599993p-9},
            {0x1.ffffffffffffep-9,      0x1.ffff555599996p-9},
            {0x1.ffffffffffff8p-8,      0x1.fffd555999924p-8},
            {0x1.ffffffffffffep-8,      0x1.fffd555999929p-8},
            {0x1.47ae147ae1458p-7,      0x1.47ab48ae4593cp-7},
            {0x1.47ae147ae1464p-7,      0x1.47ab48ae45947p-7},
            {0x1.ffffffffffffep-7,      0x1.fff5559997df6p-7},
            {0x1.fffffffffffffp-7,      0x1.fff5559997df8p-7},
            {0x1.ffffffffffff9p-6,      0x1.ffd559992b1d8p-6},
            {0x1.ffffffffffffep-6,      0x1.ffd559992b1dcp-6},
            {0x1.ffffffffffff9p-5,      0x1.ff55997e030d1p-5},
            {0x1.fffffffffffffp-5,      0x1.ff55997e030d6p-5},
            {0x1.9999999999996p-4,      0x1.983d7795f4137p-4},
            {0x1.9999999999997p-4,      0x1.983d7795f4137p-4},
            {0x1.fffffffffffffp-4,      0x1.fd5992bc4b834p-4},
            {0x1.0p-3,                  0x1.fd5992bc4b834p-4},
            {0x1.fffffffffffffp-3,      0x1.f597ea69a1c86p-3},
            {0x1.ffffffffffffcp-2,      0x1.d9353d7568aefp-2},
            {0x1.ffffffffffffep-2,      0x1.d9353d7568af3p-2},
            {0x1.ffffffffffffbp-1,      0x1.85efab514f393p-1},
            {0x1.ffffffffffffep-1,      0x1.85efab514f393p-1},
            {0x1.fffffffffffd3p0,       0x1.ed9505e1bc3cep-1},
            {0x1.fffffffffffe1p0,       0x1.ed9505e1bc3cfp-1},
            {0x1.ffffffffffed8p1,       0x1.ffa81708a0b4p-1},
            {0x1.fffffffffff92p1,       0x1.ffa81708a0b41p-1},

             // Julia worst-case input
            {0x1.0108b83c4bbc8p-1,      0x1.dad53a45da5b0p-2},

            // Empirical worst-case points in other libraries with
            // larger worst-case errors than FDLIBM
            {-0x1.c41e527b70f43p-3,    -0x1.bcea047cc736cp-3},
        };

        for (double[] testCase: testCases)
            failures += testTanhCase(testCase[0], testCase[1]);

        return failures;
    }

    private static int testAsinh() {
        int failures = 0;
        double [][] testCases = {
                {0x1.5798ee2308c36p-27, 0x1.5798ee2308c35p-27},
                {0x1.ffffffffffffep-26, 0x1.ffffffffffffdp-26},
                {0x1.ffffffffffffep-25, 0x1.ffffffffffff9p-25},
                {0x1.ad7f29abcaf47p-24, 0x1.ad7f29abcaf3bp-24},
                {0x1.ad7f29abcaf48p-24, 0x1.ad7f29abcaf3cp-24},
                {0x1.ffffffffffffep-24, 0x1.fffffffffffe9p-24},
                {0x1.ffffffffffffep-23, 0x1.fffffffffffa9p-23},
                {0x1.ffffffffffffep-22, 0x1.ffffffffffea9p-22},
                {0x1.ffffffffffffep-21, 0x1.ffffffffffaa9p-21},
                {0x1.0c6f7a0b5ed8dp-20, 0x1.0c6f7a0b5ea7ap-20},
                {0x1.ffffffffffffep-20, 0x1.fffffffffeaa9p-20},
                {0x1.ffffffffffffep-19, 0x1.fffffffffaaa9p-19},
                {0x1.fffffffffffffp-18, 0x1.ffffffffeaaa9p-18},
                {0x1p-17,               0x1.ffffffffeaaabp-18},
                {0x1.4f8b588e368edp-17, 0x1.4f8b588e1e89ep-17},
                {0x1.fffffffffffffp-17, 0x1.ffffffffaaaa9p-17},
                {0x1.fffffffffffffp-16, 0x1.fffffffeaaaa9p-16},
                {0x1p-15,               0x1.fffffffeaaaabp-16},
                {0x1.fffffffffe5ddp-15, 0x1.fffffffaa9087p-15},
                {0x1.fffffffffffffp-15, 0x1.fffffffaaaaa9p-15},
                {0x1.a36e2eb1c432dp-14, 0x1.a36e2ea609cc8p-14},
                {0x1.ffffffffffffep-14, 0x1.ffffffeaaaaa9p-14},
                {0x1p-13,               0x1.ffffffeaaaaabp-14},
                {0x1.ffffffffffd51p-13, 0x1.ffffffaaaa7fdp-13},
                {0x1.fffffffffffffp-13, 0x1.ffffffaaaaaadp-13},
                {0x1.ffffffffffffep-12, 0x1.fffffeaaaaacfp-12},
                {0x1p-11,               0x1.fffffeaaaaad1p-12},
                {0x1.fffffffffff1p-11,  0x1.fffffaaaaac21p-11},
                {0x1p-10,               0x1.fffffaaaaad11p-11},
                {0x1.0624dd2f1a9c6p-10, 0x1.0624da5218b5fp-10},
                {0x1.0624dd2f1a9f8p-10, 0x1.0624da5218b91p-10},
                {0x1.fffffffffffddp-10, 0x1.ffffeaaaad0edp-10},
                {0x1.fffffffffffffp-10, 0x1.ffffeaaaad10fp-10},
                {0x1.ffffffffffffcp-9,  0x1.ffffaaaad110cp-9},
                {0x1.ffffffffffffep-9,  0x1.ffffaaaad110ep-9},
                {0x1.ffffffffffff8p-8,  0x1.fffeaaad110aep-8},
                {0x1.ffffffffffffep-8,  0x1.fffeaaad110b4p-8},
                {0x1.47ae147ae1458p-7,  0x1.47acae9508ae4p-7},
                {0x1.47ae147ae1464p-7,  0x1.47acae9508afp-7},
                {0x1.ffffffffffffep-7,  0x1.fffaaad10fa35p-7},
                {0x1.fffffffffffffp-7,  0x1.fffaaad10fa35p-7},
                {0x1.ffffffffffff9p-6,  0x1.ffeaad10b5b28p-6},
                {0x1.ffffffffffffep-6,  0x1.ffeaad10b5b2bp-6},
                {0x1.ffffffffffff9p-5,  0x1.ffaad0fa4525bp-5},
                {0x1.fffffffffffffp-5,  0x1.ffaad0fa45261p-5},
                {0x1.9999999999996p-4,  0x1.98eb9e7e5fc3ap-4},
                {0x1.9999999999997p-4,  0x1.98eb9e7e5fc3bp-4},
                {0x1.fffffffffffffp-4,  0x1.fead0b6996972p-4},
                {0x1p-3,                0x1.fead0b6996972p-4},
                {0x1.fffffffffffffp-3,  0x1.facfb2399e636p-3},
                {0x1.ffffffffffffcp-2,  0x1.ecc2caec51605p-2},
                {0x1.ffffffffffffep-2,  0x1.ecc2caec51608p-2},
                {0x1.ffffffffffffbp-1,  0x1.c34366179d423p-1},
                {0x1.ffffffffffffep-1,  0x1.c34366179d426p-1},
                {0x1.fffffffffffd3p+0,  0x1.719218313d073p+0},
                {0x1.fffffffffffe1p+0,  0x1.719218313d079p+0},
                {0x1.ffffffffffed8p+1,  0x1.0c1f8a6e80ea4p+1},
                {0x1.fffffffffff92p+1,  0x1.0c1f8a6e80edp+1},
                {0x1.0108b83c4bbc8p-1,  0x1.ee9c256f3947ep-2},
                {-0x1.c41e527b70f43p-3, -0x1.c0863c7dece22p-3},
        };

        for (double[] testCase: testCases) {
            failures += testAsinhCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testAcosh() {
        int failures = 0;
        double [][] testCases = {
                {0x1.00020000aaaabp+0,      0x1.fffffffff749fp-8},
                {0x1.000346de27853p+0,      0x1.47ae147ae274p-7},
                {0x1.0008000aaab05p+0,      0x1.fffffffffe9f1p-7},
                {0x1.0008000aaab05p+0,      0x1.fffffffffe9f1p-7},
                {0x1.002000aaac169p+0,      0x1.fffffffffe67bp-6},
                {0x1.002000aaac16bp+0,      0x1.ffffffffff679p-6},
                {0x1.00800aab05b1ep+0,      0x1.ffffffffffc9cp-5},
                {0x1.00800aab05b1fp+0,      0x1.ffffffffffe9bp-5},
                {0x1.0147f40224b2ep+0,      0x1.9999999999318p-4},
                {0x1.0147f40224b35p+0,      0x1.9999999999776p-4},
                {0x1.0200aac16db6cp+0,      0x1.ffffffffffe91p-4},
                {0x1.0200aac16db6ep+0,      0x1.fffffffffff91p-4},
                {0x1.080ab05ca613bp+0,      0x1.ffffffffffea5p-3},
                {0x1.080ab05ca6146p+0,      0x1.0000000000001p-2},
                {0x1.20ac1862ae8cep+0,      0x1.fffffffffffedp-2},
                {0x1.20ac1862ae8dp+0,       0x1.ffffffffffffdp-2},
                {0x1.8b07551d9f551p+0,      0x1p+0},
                {0x1.e18fa0df2d9b3p+1,      0x1.ffffffffffffbp+0},
                {0x1.e18fa0df2d9b8p+1,      0x1.ffffffffffffep+0},
                {0x1.e18fa0df2d9bap+1,      0x1.fffffffffffffp+0},
                {0x1.b4ee858de3e68p+4,      0x1.ffffffffffff9p+1},
                {0x1.b4ee858de3e7ap+4,      0x1.ffffffffffffep+1},
                {0x1.b4ee858de3e7dp+4,      0x1.fffffffffffffp+1},
                {0x1.749eaa93f4e5ep+10,     0x1.ffffffffffffcp+2},
                {0x1.749eaa93f4e64p+10,     0x1.ffffffffffffdp+2},
                {0x1.749eaa93f4e76p+10,     0x1p+3},
                {0x1.0f2ebd0a7fb9p+22,      0x1.fffffffffff6fp+3},
                {0x1.0f2ebd0a8005cp+22,     0x1p+4},
                {0x1.1f43fcc4b6316p+45,     0x1.fffffffffffd3p+4},
                {0x1.1f43fcc4b662cp+45,     0x1.fffffffffffffp+4},
                {0x1.fdf25fc26e7cp+1023,    0x1.633c654fee2bap+9},
                {0x1.fdf25fc26e7cp+1023,    0x1.633c654fee2bap+9},
                {0x1.e0976c8f0ebdfp+1,      0x1.ff76fb3f476d5p+0},
                {0x1.ff66e0de4dc6fp+1023,   0x1.633cc2ae1c934p+9},
                {0x1.f97ccb0aef314p+11,     0x1.1ff088806d82ep+3},
                {0x1.fdf28623ef923p+1021,   0x1.628af341989dap+9},
        };

        for (double[] testCase: testCases) {
            failures += testAcoshCase(testCase[0], testCase[1]);
        }

        return failures;
    }
}
