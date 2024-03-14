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

        // Inputs where Math.sin and StrictMath.sin differ for at least
        // one Math.sin implementation.
        double [][] testCases = {
            {0x1.00000006eeeefp-12,  0x1.ffffffb888889p-13},
            {0x1.00000006eeefp-12,   0x1.ffffffb88888bp-13},
            {0x1.00000006eeef1p-12,  0x1.ffffffb88888dp-13},
            {0x1.000000001bba2p-9,   0x1.ffffeaaae2633p-10},
            {0x1.000000000013p-1,    0x1.eaee8744b0806p-2},
            {0x1.0000000000012p0,    0x1.aed548f090d02p-1},
            {0x1.00000000004e1p9,    0x1.45b52f29ac36p-4},
            {0x1.00000000000cp10,   -0x1.44ad26136ce5fp-3},
            {0x1.000000000020bp11,  -0x1.4092047afcd2p-2},
            {0x1.0000000000003p12,  -0x1.3074ea23314dep-1},
            {0x1.0000000000174p50,  -0x1.54cd5e7e9e3d2p-1},
            {0x1.0000000000005p51,  -0x1.8c35b0d728faep-2},
            {0x1.0000000000101p113, -0x1.69e9ed300b1dcp-1},
            {0x1.0000000000017p114,  0x1.f6b44aa2a1c9cp-1},
            {0x1.00000000001abp128, -0x1.ecaddc1136bb2p-1},
            {0x1.000000000001bp129, -0x1.682ccb977e4dp-1},
            {0x1.0p233,              0x1.7c54e75ed6077p-1},
            {0x1.00000000000fcp299,  0x1.78ad2fd7aef78p-1},
            {0x1.0000000000002p300, -0x1.1adaf3550facp-1},
            {0x1.00000000001afp1023, 0x1.d1c804ef2eeccp-1},
            // Empirical worst-case points
            {-0x1.f8b791cafcdefp+4,  -0x1.073ca87470dfap-3},
            {-0x1.0e16eb809a35dp+944, 0x1.b5e361ed01dadp-2},
            {-0x1.842d8ec8f752fp+21, -0x1.6ce864edeaffep-1},
            {-0x1.1c49ad613ff3bp+19, -0x1.fffe203cfabe1p-2},
        };

        for (double[] testCase: testCases) {
            failures+=testSinCase(testCase[0], testCase[1]);
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

        // Inputs where Math.cos and StrictMath.cos differ for at least
        // one Math.cos implementation.
        double [][] testCases = {
            {0x1.000000076aaa6p-10,   0x1.fffff00000147p-1},
            {0x1.000000002e4fbp-8,    0x1.ffff00001554fp-1},
            {0x1.0000000000318p-2,    0x1.f01549f7dee4p-1},
            {0x1.000000000011ep-1,    0x1.c1528065b7cc6p-1},
            {0x1.0000000000174p0,     0x1.14a280fb50419p-1},
            {0x1.0000000000019p1,    -0x1.aa226575372bbp-2},
            {0x1.00000000018c9p9,    -0x1.fe60f23b0016ap-1},
            {0x1.0000000000022p10,    0x1.f98669d7b18d6p-1},
            {0x1.0000000000281p11,    0x1.e6439428b217p-1},
            {0x1.0000000000001p12,    0x1.9ba4a85e6e173p-1},
            {0x1.0000000000211p20,    0x1.e33ad93554beep-1},
            {0x1.0000000000006p21,    0x1.9027223f77694p-1},
            {0x1.00000000000b8p95,    0x1.8315138968a66p-1},
            {0x1.0000000000043p96,    0x1.5b302d1c86cbcp-4},
            {0x1.000000000013ap127,  -0x1.740d46d7821f4p-1},
            {0x1.0000000000002p128,  -0x1.e050345cf2161p-1},
            {0x1.000000000014p299,    0x1.6c5f3c84352fep-1},
            {0x1.0000000000007p300,  -0x1.55109bfdf1c5cp-1},
            {0x1.000000000010ep400,   0x1.e725637029938p-2},
            {0x1.0000000000007p401,   0x1.1f89e14e29ccep-1},
            {0x1.0p402,               0x1.be2d53c4560dcp-1},
            {0x1.000000000015fp1023, -0x1.2f2596c42735cp-1},
        };

        for (double[] testCase: testCases) {
            failures+=testCosCase(testCase[0], testCase[1]);
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

        // Inputs where Math.tan and StrictMath.tan differ for at least
        // one Math.tan implementation.
        double [][] testCases = {
            {0x1.00000002221fep-13,  0x1.0000001777753p-13},
            {0x1.0000000088859p-12,  0x1.00000055dddbp-12},
            {0x1.0000000008787p-10,  0x1.000005555defep-10},
            {0x1.0000000001423p-9,   0x1.0000155558b9ap-9},
            {0x1.00000000005d9p-2,   0x1.05785a43c529p-2},
            {0x1.000000000001fp-1,   0x1.17b4f5bf34772p-1},
            {0x1.000000000006ep0,    0x1.8eb245cbee51ep0},
            {0x1.0000000000032p1,   -0x1.17af62e094fd7p1},
            {0x1.00000000006a7p9,   -0x1.46be0efd0f8cp-4},
            {0x1.0p10,              -0x1.48d5be43ada01p-3},
            {0x1.00000000000c3p32,   0x1.0ad3757181cbap-1},
            {0x1.0000000000005p33,   0x1.6e07fbf43d47p0},
            {0x1.0000000000124p127, -0x1.3baa73a93958p0},
            {0x1.000000000002p128,  -0x1.bf05a77a8df0cp-1},
            {0x1.000000000011cp299,  0x1.8a6f42eaa3d1fp0},
            {0x1.000000000001cp300, -0x1.b30fc9f73002cp-1},
            {0x1.0000000000013p500, -0x1.c4e46751be12cp-1},
            {0x1.00000000000ep1023, -0x1.d52c4ec04f108p-2},
            // Empirical worst-case points in other libraries with
            // larger worst-case errors than FDLIBM
            {+0x1.371a47b7e4eb2p+11,    0x1.9ded57c9ff46ap-1},
            {-0x1.a81d98fc58537p+6 ,    0x1.ffd83332326fdp-1},
            {-0x1.13a5ccd87c9bbp+1008, -0x1.6a3815320e5cfp-1},
            {-0x1.4d7c8b8320237p+11,   -0x1.9dec1f1b36ecdp-1},
            {+0x1.da7a85a88bbecp+11,    0x1.ff7ae7631230ep-1},
            {-0x1.66af736e8555p+18,    -0x1.fc3d1cb02536bp-1},
        };

        for (double[] testCase: testCases) {
            failures+=testTanCase(testCase[0], testCase[1]);
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
