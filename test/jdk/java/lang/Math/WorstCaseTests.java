/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4900206 8316708
 * @summary Test worst case behavior of exp, log, sin, cos, etc.
 * @build Tests
 * @build WorstCaseTests
 * @run main WorstCaseTests
 * @run main/othervm -Xcomp WorstCaseTests
 */

/**
 * This test contains two distinct kinds of worst-case inputs:
 *
 * 1) Exact numerical results that are nearly half-way between
 * representable numbers or very close to a representable
 * number. (Half-way cases are hardest for round to nearest even;
 * close to a representable number cases are hard for directed
 * roundings.)
 *
 * 2) Worst-case errors as observed empirically across different
 * implementations that are not correctly rounded.
 *
 * For the first category, the "Table Maker's Dilemma" results from
 * Jean-Michel Muller and Vincent Lef&egrave;vre, are used.
 * See https://perso.ens-lyon.fr/jean-michel.muller/TMD.html for original
 * test vectors from 2000 and see
 * https://perso.ens-lyon.fr/jean-michel.muller/TMDworstcases.pdf with
 * additional test vectors from 2003.  The latter link also contains
 * some information about the methodology used to produce the test
 * vectors.
 *
 * Most of the Java math library methods tested here have a 1-ulp
 * error bound from their specifications.  This implies the returned
 * value must be one of the two representable floating-point numbers
 * bracketing the exact result.  The expected value in the test
 * vectors below is the truncation of the exact value.  Therefore, the
 * computed result must either be that value or the value next larger
 * in magnitude.  The hyperbolic transcendental functions sinh and cosh
 * have a larger 2.5 ulp error bound in their specification, but the
 * JDK implementation complies with a 1 ulp bound on the worst-case
 * values.  Therefore, no addition leeway is afforded when testing
 * sinh and cosh.
 *
 * For the second category, worst-case observed error inputs for the
 * FDLIBM-derived OpenLibm 0.8.1 and other math libraries are added
 * from "Accuracy of Mathematical Functions in Single, Double, Double
 * Extended, and Quadruple Precision" by Brian Gladman, Vincenzo
 * Innocente and Paul Zimmermann.
 *
 * From https://openlibm.org/, "The OpenLibm code derives from the
 * FreeBSD msun and OpenBSD libm implementations, which in turn derive
 * from FDLIBM 5.3." Java's StrictMath libraries use the FDLIBM 5.3
 * algorithms.
 */
public class WorstCaseTests {
    private WorstCaseTests() {throw new AssertionError("No instances for you.");}

    public static void main(String... args) {
        int failures = 0;

        failures += testWorstExp();
        failures += testWorstLog();
        failures += testWorstSin();
        failures += testWorstAsin();
        failures += testWorstCos();
        failures += testWorstAcos();
        failures += testWorstTan();
        failures += testWorstAtan();
        failures += testWorstAtan2();
        failures += testWorstPow2();
        failures += testWorstSinh();
        failures += testWorstCosh();
        failures += testWorstHypot();

        if (failures > 0) {
            System.err.printf("Testing worst cases incurred %d failures.%n", failures);
            throw new RuntimeException();
        }
    }

    /*
     * 1 ulp stated error bound
     */
    private static int testWorstExp() {
        int failures = 0;
        double [][] testCases = {
            {-0x1.E8BDBFCD9144Ep3,      0x1.F3E558CF4DE54p-23},
            {-0x1.71E0B869B5E79p2,      0x1.951C6DC5D24E2p-9},
            {-0x1.02393D5976769p1,      0x1.1064B2C103DDAp-3},
            {-0x1.2A9CAD9998262p0,      0x1.3EF1E9B3A81C7p-2},
            {-0x1.CC37EF7DE7501p0,      0x1.534D4DE870713p-3},
            {-0x1.22E24FA3D5CF9p-1,     0x1.2217147B85EA9p-1},
            {-0x1.DC2B5DF1F7D3Dp-1,     0x1.9403FD0EE51C8p-2},
            {-0x1.290EA09E36479p-3,     0x1.BADED30CBF1C3p-1},
            {-0x1.A2FEFEFD580DFp-13,    0x1.FFE5D0BB7EABFp-1},
            {-0x1.ED318EFB627EAp-27,    0x1.FFFFFF84B39C4p-1},
            {-0x1.4BD46601AE1EFp-31,    0x1.FFFFFFFAD0AE6p-1},
            {-0x1.1000000000242p-42,    0x1.FFFFFFFFFF780p-1},
            {-0x1.2000000000288p-42,    0x1.FFFFFFFFFF700p-1},
            {-0x1.8000000000012p-48,    0x1.FFFFFFFFFFFD0p-1},
            {-0x1.0000000000001p-51,    0x1.FFFFFFFFFFFFCp-1},

            {+0x1.FFFFFFFFFFFFFp-53,    0x1.0000000000000p0},
            {+0x1.FFFFFFFFFFFE0p-48,    0x1.000000000001Fp0},
            {+0x1.7FFE7FFEE0024p-32,    0x1.000000017FFE8p0},
            {+0x1.80017FFEDFFDCp-32,    0x1.0000000180017p0},
            {+0x1.9E9CBBFD6080Bp-31,    0x1.000000033D397p0},
            {+0x1.D7A7D893609E5p-26,    0x1.00000075E9F64p0},
            {+0x1.BA07D73250DE7p-14,    0x1.0006E83736F8Cp0},
            {+0x1.D77FD13D27FFFp-11,    0x1.003AF6C37C1D3p0},
            {+0x1.6A4D1AF9CC989p-8,     0x1.016B4DF3299D7p0},
            {+0x1.ACCFBE46B4EF0p-1,     0x2.4F85C9783DCE0p0},
            {+0x1.ACA7AE8DA5A7Bp0,      0x5.55F52B35F955Ap0},
            {+0x1.D6336A88077AAp0,      0x6.46A37FD503FDCp0},
            {+0x2.85DC78FB8928Cp0,      0xC.76F2496CB038Fp0},
            {+0x1.76E7E5D7B6EACp3,      0x1.DE7CD6751029Ap16},
            {+0x1.A8EAD058BC6B8p3,      0x1.1D71965F516ADp19},
            {+0x1.1D5C2DAEBE367p4,      0x1.A8C02E974C314p25},
            {+0x1.C44CE0D716A1Ap4,      0x1.B890CA8637AE1p40},

            // Worst-case observed error for OpenLibm
            {+0x1.2e8f20cf3cbe7p+8,     0x1.6a2a59cc78bf7p436},
            // Other worst-case observed errors
            {-0x1.49f33ad2c1c58p+9,     0x1.f3ccc815431b5p-953},
            {+0x1.fce66609f7428p+5,     0x1.b59724cb0bc4cp91},
            {+0x1.b97dc8345c55p+5,      0x1.88ab482dafdd7p79},
            {-0x1.18209ecd19a8cp+6,     0x1.f3dcee4c90df9p-102},
            {-0x1.4133f4fd79c1cp-13,    0x1.ffebed256fadp-1},
            {-0x1.74046dfefd9d1p+9,     0x0.0000000000001p-1022},
            {-0x1.49f33ad2c1c58p+9,     0x1.f3ccc815431b5p-953},
        };

        for(double[] testCase: testCases) {
            failures += testExpCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testExpCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.exp",       input, Math::exp,       expected, out);
        failures += Tests.testBounds("StrictMath.exp", input, StrictMath::exp, expected, out);
        return failures;
    }

    /*
     * 1 ulp stated error bound
     */
    private static int testWorstLog() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.0000000000001p0,      +0x1.FFFFFFFFFFFFFp-53},
            {+0x2.0012ECB039C9Cp0,      +0x1.62F71C4656B60p-1},
            {+0x6.46A37FD503FDCp0,      +0x1.D6336A88077A9p+0},
            {+0x7.78DFECC7F57Fp0,       +0x2.02DD059DB46Bp+0},
            {+0x9.588CCF24BB9C8p0,      +0x2.3C24DEBB2BE7p+0},
            {+0xA.AF87550D97E4p0,       +0x2.5E706595A7ABEp+0},
            {+0xC.76F2496CB039p0,       +0x2.85DC78FB8928Cp+0},
            {+0x11.1867637CBD03p0,      +0x2.D6BBEFC79A842p+0},
            {+0x13.D9D7D597A9DDp0,      +0x2.FCFE12AE07DDCp+0},
            {+0x17.F3825778AAAFp0,      +0x3.2D0F907F5E00Cp+0},
            {+0x1AC.50B409C8AEEp0,      +0x6.0F52F37AECFCCp+0},
            {+0x1.DE7CD6751029Ap16,     +0x1.76E7E5D7B6EABp+3},

            // Worst-case observed error for OpenLibm
            {+0x1.48ae5a67204f5p+0,     0x1.ffd10abffc3fep-3},
            // Other worst-case observed errors
            {+0x1.1211bef8f68e9p+0,     +0x1.175caeca67f84p-4},
            {+0x1.008000db2e8bep+0,     +0x1.ff83959f5cc1fp-10},
            {+0x1.0ffea3878db6bp+0,     +0x1.f07a0cca521efp-5},
            {+0x1.dc0b586f2b26p-1,      -0x1.2a3eaaa6e8d72p-4},
            {+0x1.490af72a25a81p-1,     -0x1.c4bf7ae48f078p-2},
            {+0x1.5b6e7e4e96f86p+2,     +0x1.b11240cba290dp0},
            {+0x1.0ffc349469a2fp+0,     +0x1.f030c2507cd81p-5},
            {+0x1.69e7aa6da2df5p-1,     -0x1.634508c9adfp-2},
            {+0x1.5556123e8a2bp-1,      -0x1.9f300810f7d7cp-2},
        };

        for(double[] testCase: testCases) {
            failures += testLogCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testLogCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.log",       input, Math::log,       expected, out);
        failures += Tests.testBounds("StrictMath.log", input, StrictMath::log, expected, out);
        return failures;
    }

    /*
     * 1 ulp stated error bound
     */
    private static int testWorstSin() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.E0000000001C2p-20,    +0x1.DFFFFFFFFF02Ep-20},
            {+0x1.598BAE9E632F6p-7,     +0x1.598A0AEA48996p-7},

            {+0x1.9283586503FEp-5,      +0x1.9259E3708BD39p-5},
            {+0x1.D7BDCD778049Fp-5,     +0x1.D77B117F230D5p-5},
            {+0x1.A202B3FB84788p-4,     +0x1.A1490C8C06BA6p-4},
            {+0x1.D037CB27EE6DFp-3,     +0x1.CC40C3805229Ap-3},
            {+0x1.D5064E6FE82C5p-3,     +0x1.D0EF799001BA9p-3},
            {+0x1.FE767739D0F6Dp-2,     +0x1.E9950730C4695p-2},
            {+0x1.D98C4C612718Dp-1,     +0x1.98DCD09337792p-1},
            {+0x1.921FB54442D18p-0,     +0x1.FFFFFFFFFFFFFp-1},

            {+0x1.6756745770A51p+1,     +0x1.4FF350E412821p-2},

            // Worst-case observed error for OpenLibm
            {+0x1.4d84db080b9fdp+21,    +0x1.6e21c4ff6aec3p-1},
            // Other worst-case observed errors
            {-0x1.f8b791cafcdefp+4,     -0x1.073ca87470df9p-3 },
            {-0x1.0e16eb809a35dp+944,   +0x1.b5e361ed01dacp-2},
            {-0x1.85e624577c23ep-1,     -0x1.614ac15b6df5ap-1},
            {-0x1.842d8ec8f752fp+21,    -0x1.6ce864edeaffdp-1},
            {-0x1.07e4c92b5349dp+4,     +0x1.6a096375ffb23p-1},
            {-0x1.13a5ccd87c9bbp+1008,  -0x1.27b3964185d8dp-1},
            {-0x1.11b624b546894p+9,     -0x1.6a35f2416aba8p-1},
            {-0x1.1c49ad613ff3bp+19,    -0x1.fffe203cfabe1p-2},
            {-0x1.f05e952d81b89p+5,     +0x1.6a2319a85a544p-1},
        };

        for(double[] testCase: testCases) {
            failures += testSinCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testSinCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.sin",       input, Math::sin,       expected, out);
        failures += Tests.testBounds("StrictMath.sin", input, StrictMath::sin, expected, out);
        return failures;
    }

    /*
     * 1 ulp stated error bound
     */
    private static int testWorstAsin() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.DFFFFFFFFF02Ep-20,    +0x1.E0000000001C1p-20},
            {+0x1.DFFFFFFFFC0B8p-19,    +0x1.E000000000707p-19},

            {+0x1.9259E3708BD3Ap-5,     +0x1.9283586503FEp-5},
            {+0x1.D77B117F230D6p-5,     +0x1.D7BDCD778049Fp-5},
            {+0x1.A1490C8C06BA7p-4,     +0x1.A202B3FB84788p-4},
            {+0x1.9697CB602C582p-3,     +0x1.994FFB5DAF0F9p-3},
            {+0x1.D0EF799001BA9p-3,     +0x1.D5064E6FE82C4p-3},
            {+0x1.E9950730C4696p-2,     +0x1.FE767739D0F6Dp-2},
            {+0x1.1ED06D50F7E88p-1,     +0x1.30706F699466Dp-1},
            {+0x1.D5B05A89D3E77p-1,     +0x1.29517AB4C132Ap+0},
            {+0x1.E264357EA0E29p-1,     +0x1.3AA301F6EBB1Dp+0},

            // Worst-case observed error for OpenLibm
            {-0x1.004d1c5a9400bp-1,    -0x1.0c6e322e8a28bp-1},
            // Other worst-case observed errors
            {-0x1.0000045b2c904p-3,     -0x1.00abe5252746cp-3},
            {+0x1.6c042a6378102p-1,     +0x1.94eda53f72c5ap-1},
            {-0x1.00d44cccfa99p-1,      -0x1.0d0a6a0e79e15p-1},
            {+0x1.eae75e3d82b6fp-2,     +0x1.fff7d74b1ea4fp-2},
            {-0x1.0239000439deep-1,     -0x1.0ea71ea2a7cd7p-1},
            {+0x1.0479b37d95e5cp-1,     +0x1.1143fafdc5b2cp-1},
            {-0x1.2ef2481799c7cp-1,     -0x1.442d10aa50906p-1},
            {+0x1.df27e1c764802p-2,     +0x1.f2a0f0c96deefp-2},
        };

        for(double[] testCase: testCases) {
            failures += testAsinCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testAsinCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.asin",       input, Math::asin,       expected, out);
        failures += Tests.testBounds("StrictMath.asin", input, StrictMath::asin, expected, out);
        return failures;
    }

    /*
     * 1 ulp stated error bound
     */
    private static int testWorstCos() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.8000000000009p-23,    +0x0.FFFFFFFFFFFB8p+0},
            {+0x1.8000000000024p-22,    +0x0.FFFFFFFFFFEE0p+0},
            {+0x1.2000000000F30p-18,    +0x0.FFFFFFFFF5E00p+0},
            {+0x1.06B505550E6B2p-9,     +0x0.FFFFDE4D1FDFFp+0},
            {+0x1.97CCD3D2C438Fp-6,     +0x0.FFEBB35D43854p+0},

            {+0x1.549EC0C0C5AFAp-5,     +0x1.FF8EB6A91ECB0p-1},
            {+0x1.16E534EE36580p-4,     +0x1.FED0476FC75C9p-1},
            {+0x1.EFEEF61D39AC2p-3,     +0x1.F10FC61E2C78Ep-1},
            {+0x1.FEB1F7920E248p-2,     +0x1.C1A27AE836F12p-1},
            {+0x1.7CB7648526F99p-1,     +0x1.78DAF01036D0Cp-1},
            {+0x1.C65A170474549p-1,     +0x1.434A3645BE208p-1},
            {+0x1.6B8A6273D7C21p+0,     +0x1.337FC5B072C52p-3},

            // Worst-case observed error for OpenLibm
            {-0x1.34e729fd08086p+21,    +0x1.6a6a0d6a17f0fp-1},
            // Other worst-case observed errors
            {-0x1.7120161c92674p+0,     +0x1.0741fb7683849p-3},
            {-0x1.d19ebc5567dcdp+311,   -0x1.b5d2f45f68958p-2},
            {+0x1.91e60af551108p-1,     +0x1.6a32aaa34b118p-1},
            {-0x1.4ae182c1ab422p+21,    -0x1.6c9c3831b6e3bp-1},
            {-0x1.34e729fd08086p+21,    +0x1.6a6a0d6a17f0fp-1},
            {+0x1.2f29eb4e99fa2p+7,     +0x1.6a0751dc5d2bbp-1},
            {-0x1.9200634d4471fp-1,     +0x1.6a200b493230cp-1},
            {+0x1.25133ca3904dfp+20,    -0x1.fb399cd6fe563p-3},
            {+0x1.2a33ae49ab15dp+1,     -0x1.60524e89bbcb2p-1},
        };

        for(double[] testCase: testCases) {
            failures += testCosCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testCosCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.cos",       input, Math::cos,       expected, out);
        failures += Tests.testBounds("StrictMath.cos", input, StrictMath::cos, expected, out);
        return failures;
    }

    /*
     * 1 ulp stated error bound
     */
    private static int testWorstAcos() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.FD737BE914578p-11,    +0x1.91E006D41D8D8p+0},
            {+0x1.4182199998587p-1,     +0x1.C8A538AE83D1Fp-1},
            {+0x1.E45A1C93651ECp-1,     +0x1.520DC553F6B23p-2},
            {+0x1.F10FC61E2C78Fp-1,     +0x1.EFEEF61D39AC1p-3},

            // Worst-case observed error for OpenLibm
            {-0x1.0068b067c6feep-1,     +0x1.0c335e2f0726fp1},
            // Other worst-case observed errors
            {+0x1.dffffb3488a4p-1,      0x1.6bf3a4a4f4dcbp-2},
            {+0x1.6c05eb219ec46p-1,     0x1.8f4f472807261p-1},
            {+0x1.35b03e336a82bp-1,     0x1.d7a84ec2f6707p-1},
            {-0x1.8d313198a2e03p-53,    0x1.921fb54442d19p0},
            {-0x1.010fd0ad6aa41p-1,     0x1.0c63a8cd23beep1},
            {+0x1.251869c3f7881p-1,     0x1.ec2ff0c102683p-1},
            {+0x1.266637a3d2bbcp-1,     0x1.ea98637533648p-1},
            {-0x1.36b1482765f6dp-1,     0x1.1c8666ca1cab1p1},
        };

        for(double[] testCase: testCases) {
            failures += testAcosCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testAcosCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.acos",       input, Math::acos,       expected, out);
        failures += Tests.testBounds("StrictMath.acos", input, StrictMath::acos, expected, out);
        return failures;
    }

    /*
     * 1.25 ulp stated error bound
     */
    private static int testWorstTan() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.DFFFFFFFFFF1Fp-22,    +0x1.E000000000151p-22},
            {+0x1.67FFFFFFFA114p-18,    +0x1.6800000008E61p-18},

            {+0x1.50486B2F87014p-5,     +0x1.5078CEBFF9C72p-5},
            {+0x1.52C39EF070CADp-4,     +0x1.5389E6DF41978p-4},
            {+0x1.A33F32AC5CEB5p-3,     +0x1.A933FE176B375p-3},
            {+0x1.D696BFA988DB9p-2,     +0x1.FAC71CD34EEA6p-2},
            {+0x1.46AC372243536p-1,     +0x1.7BA49F739829Ep-1},
            {+0x0.A3561B9121A9Bp+0,     +0x0.BDD24FB9CC14Fp+0},

            // Worst-case observed error for OpenLibm, outside of 1 ulp error
            // {0x1.3f9605aaeb51bp+21,     -0x1.9678ee5d64934p-1}, // 1.02
        };

        for(double[] testCase: testCases) {
            failures += testTanCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testTanCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.tan",       input, Math::tan,       expected, out);
        failures += Tests.testBounds("StrictMath.tan", input, StrictMath::tan, expected, out);
        return failures;
    }

    /*
     * 1 ulp stated error bound
     */
    private static int testWorstAtan() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.E000000000546p-21,     +0x1.DFFFFFFFFFC7Cp-21},
            {+0x1.22E8D75E2BC7Fp-11,     +0x1.22E8D5694AD2Bp-11},

            {+0x1.0FC9F1FABE658p-5,     +0x1.0FB06EDE9973Ap-5},
            {+0x1.1BBE9C255698Dp-5,     +0x1.1BA1951DB1D6Dp-5},
            {+0x1.8DDD25AB90CA1p-5,     +0x1.8D8D2D4BD6FA2p-5},
            {+0x1.5389E6DF41979p-4,     +0x1.52C39EF070CADp-4},
            {+0x1.A933FE176B375p-3,     +0x1.A33F32AC5CEB4p-3},
            {+0x1.0F6E5D9960397p-2,     +0x1.09544B71AD4A6p-2},
            {+0x1.7BA49F739829Fp-1,     +0x1.46AC372243536p-1},

            {+0x0.BDD24FB9CC14F8p+0,    +0x0.A3561B9121A9Bp+0},

            // Worst-case observed error
            {0x1.62ff6a1682c25p-1,      +0x1.3666b15c8756ap-1},
            // Other worst-case observed errors
            {+0x1.f9004c4fef9eap-4,  0x1.f67727f5618f2p-4},
            {-0x1.ffff8020d3d1dp-7, -0x1.fff4d5e4886c7p-7},
            {+0x1.0103fc4ebaaa8p+1,  0x1.1bd5c375c97b5p0},
            {+0x1.01e0be37af68fp+1,  0x1.1c2d45ec82765p0},
            {-0x1.60370d15396b7p-1, -0x1.348461c347fd9p-1},
            {+0x1.032b4811f3dc5p+0,  0x1.9545fd233ee14p-1},
            {+0x1.52184b1b9bd9bp+0,  0x1.d86de33c93814p-1},
            {-0x1.0684fa9fa7481p+0, -0x1.988f9da70b11ap-1},

        };

        for(double[] testCase: testCases) {
            failures += testAtanCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testAtanCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.atan",       input, Math::atan,       expected, out);
        failures += Tests.testBounds("StrictMath.atan", input, StrictMath::atan, expected, out);
        return failures;
    }

    /*
     * 2 ulp stated error bound
     */
    private static int testWorstAtan2() {
        int failures = 0;
        double [][] testCases = {
            // Input with large worst-case observed error for another math library
            {-0x0.00000000039a2p-1022, 0x0.000fdf02p-1022, -0x1.d0ce6fac85de8p-27},
        };

        for(double[] testCase: testCases) {
            failures += testAtan2Case(testCase[0], testCase[1], testCase[2]);
        }

        return failures;
    }

    private static int testAtan2Case(double input1, double input2, double expected) {
        int failures = 0;
         // Cannot represent exact result, allow 1 additional ulp on top of documented bound.
        double ulps = 2.0 + 1.0;
        failures += Tests.testUlpDiff("Math.atan2",       input1, input2, Math::atan2,       expected, ulps);
        failures += Tests.testUlpDiff("StrictMath.atan2", input1, input2, StrictMath::atan2, expected, ulps);
        return failures;
    }

    /*
     * 1 ulp stated error bound
     */
    private static int testWorstPow2() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.16A76EC41B516p-1,     +0x1.7550685A42C63p+0},
            {+0x1.3E34FA6AB969Ep-1,     +0x1.89D948A94FE16p+0},
            {+0x1.4A63FF1D53F53p-1,     +0x1.90661DA12D528p+0},
            {+0x1.B32A6C92D1185p-1,     +0x1.CD6B37EDECEAFp+0},

            {+0x1.25DD9EEDAC79Ap+0,     +0x1.1BA39FF28E3E9p+1},
        };

        for(double[] testCase: testCases) {
            failures += testPow2Case(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testPow2Case(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.pow2",       input, d -> Math.pow(2, d),       expected, out);
        failures += Tests.testBounds("StrictMath.pow2", input, d -> StrictMath.pow(2, d), expected, out);
        return failures;
    }

    // 2.5 ulp error bound in the specification; the implementation
    // does better on the tested values.
    private static int testWorstSinh() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.DFFFFFFFFFE3Ep-20,     +0x1.E000000000FD1p-20},
            {+0x1.DFFFFFFFFE3E0p-18,     +0x1.E00000000FD1Fp-18},
            {+0x1.135E31FDD05D3p-5,      +0x1.136B78B25CC57p-5},
            {+0x1.0DC68D5E8F959p-3,      +0x1.0E8E73DC4FEE3p-3},
            {+0x1.616CC75D49226p-2,      +0x1.687BD068C1C1Ep-2},
            {+0x1.3FFC12B81CBC2p+0,      +0x1.9A0FF413A1AF2p+0},
            {+0x2.FE008C44BACA2p+0,      +0x9.F08A43ED03AEp+0},
            {+0x1.C089FCF166171p+4,      +0x1.5C452E0E37569p+39},
            {+0x1.E07E71BFCF06Fp+5,      +0x1.91EC4412C344Fp+85},
            {+0x1.54CD1FEA7663Ap+7,      +0x1.C90810D354618p+244},
            {+0x1.D6479EBA7C971p+8,      +0x1.62A88613629B5p+677},
        };

        for(double[] testCase: testCases) {
            failures += testSinhCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testSinhCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.sinh",       input, Math::sinh,       expected, out);
        failures += Tests.testBounds("StrictMath.sinh", input, StrictMath::sinh, expected, out);
        return failures;
    }

    // 2.5 ulp error bound in the specification; the implementation
    // does better on the tested values.
    private static int testWorstCosh() {
        int failures = 0;
        double [][] testCases = {
            {+0x1.17D8A9F206217p-6,     +0x1.00098F5F09BE3p+0},
            {+0x1.BF0305E2C6C37p-3,     +0x1.061F4C39E16F2p+0},
            {+0x1.03923F2B47C07p-1,     +0x1.219C1989E3372p+0},
            {+0x1.A6031CD5F93BAp-1,     +0x1.5BFF041B260FDp+0},
            {+0x1.104B648F113A1p+0,     +0x1.9EFDCA62B7009p+0},
            {+0x1.EA5F2F2E4B0C5p+1,     +0x17.10DB0CD0FED5p+0},
        };

        for(double[] testCase: testCases) {
            failures += testCoshCase(testCase[0], testCase[1]);
        }

        return failures;
    }

    private static int testCoshCase(double input, double expected) {
        int failures = 0;
        double out = Tests.nextOut(expected);
        failures += Tests.testBounds("Math.cosh",       input, Math::cosh,       expected, out);
        failures += Tests.testBounds("StrictMath.cosh", input, StrictMath::cosh, expected, out);
        return failures;
    }

    /*
     * 1.5 ulp stated error bound
     */
    private static int testWorstHypot() {
        int failures = 0;
        double [][] testCases = {
            // Input with large worst-case observed error for another math library
            {-0x0.fffffffffffffp-1022, 0x0.0000000000001p-1022, 0x0.fffffffffffffp-1022},
        };

        for(double[] testCase: testCases) {
            failures += testHypotCase(testCase[0], testCase[1], testCase[2]);
        }

        return failures;
    }

    private static int testHypotCase(double input1, double input2, double expected) {
        int failures = 0;
         // Cannot represent exact result, allow 1 additional ulp on top of documented bound, rounding up.
        double ulps = 3.0; // 1.5 + 1.0, rounded up
        failures += Tests.testUlpDiff("Math.hypot",       input1, input2, Math::hypot,       expected, ulps);
        failures += Tests.testUlpDiff("StrictMath.hypot", input1, input2, StrictMath::hypot, expected, ulps);
        return failures;
    }
}
