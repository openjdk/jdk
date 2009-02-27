/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 4851638 4939441
 * @summary Tests for {Math, StrictMath}.log1p
 * @author Joseph D. Darcy
 */

import sun.misc.DoubleConsts;
import sun.misc.FpUtils;

public class Log1pTests {
    private Log1pTests(){}

    static final double infinityD = Double.POSITIVE_INFINITY;
    static final double NaNd = Double.NaN;

    /**
     * Formulation taken from HP-15C Advanced Functions Handbook, part
     * number HP 0015-90011, p 181.  This is accurate to a few ulps.
     */
    static double hp15cLogp(double x) {
        double u = 1.0 + x;
        return (u==1.0? x : StrictMath.log(u)*x/(u-1) );
    }

    /*
     * The Taylor expansion of ln(1 + x) for -1 < x <= 1 is:
     *
     * x - x^2/2 + x^3/3 - ... -(-x^j)/j
     *
     * Therefore, for small values of x, log1p(x) ~= x.  For large
     * values of x, log1p(x) ~= log(x).
     *
     * Also x/(x+1) < ln(1+x) < x
     */

    static int testLog1p() {
        int failures = 0;

        double [][] testCases = {
            {Double.NaN,                NaNd},
            {Double.longBitsToDouble(0x7FF0000000000001L),      NaNd},
            {Double.longBitsToDouble(0xFFF0000000000001L),      NaNd},
            {Double.longBitsToDouble(0x7FF8555555555555L),      NaNd},
            {Double.longBitsToDouble(0xFFF8555555555555L),      NaNd},
            {Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL),      NaNd},
            {Double.longBitsToDouble(0xFFFFFFFFFFFFFFFFL),      NaNd},
            {Double.longBitsToDouble(0x7FFDeadBeef00000L),      NaNd},
            {Double.longBitsToDouble(0xFFFDeadBeef00000L),      NaNd},
            {Double.longBitsToDouble(0x7FFCafeBabe00000L),      NaNd},
            {Double.longBitsToDouble(0xFFFCafeBabe00000L),      NaNd},
            {Double.NEGATIVE_INFINITY,  NaNd},
            {-8.0,                      NaNd},
            {-1.0,                      -infinityD},
            {-0.0,                      -0.0},
            {+0.0,                      +0.0},
            {infinityD,                 infinityD},
        };

        // Test special cases
        for(int i = 0; i < testCases.length; i++) {
            failures += testLog1pCaseWithUlpDiff(testCases[i][0],
                                                 testCases[i][1], 0);
        }

        // For |x| < 2^-54 log1p(x) ~= x
        for(int i = DoubleConsts.MIN_SUB_EXPONENT; i <= -54; i++) {
            double d = FpUtils.scalb(2, i);
            failures += testLog1pCase(d, d);
            failures += testLog1pCase(-d, -d);
        }

        // For x > 2^53 log1p(x) ~= log(x)
        for(int i = 53; i <= DoubleConsts.MAX_EXPONENT; i++) {
            double d = FpUtils.scalb(2, i);
            failures += testLog1pCaseWithUlpDiff(d, StrictMath.log(d), 2.001);
        }

        // Construct random values with exponents ranging from -53 to
        // 52 and compare against HP-15C formula.
        java.util.Random rand = new java.util.Random();
        for(int i = 0; i < 1000; i++) {
            double d = rand.nextDouble();

            d = FpUtils.scalb(d, -53 - FpUtils.ilogb(d));

            for(int j = -53; j <= 52; j++) {
                failures += testLog1pCaseWithUlpDiff(d, hp15cLogp(d), 5);

                d *= 2.0; // increase exponent by 1
            }
        }

        // Test for monotonicity failures near values y-1 where y ~=
        // e^x.  Test two numbers before and two numbers after each
        // chosen value; i.e.
        //
        // pcNeighbors[] =
        // {nextDown(nextDown(pc)),
        // nextDown(pc),
        // pc,
        // nextUp(pc),
        // nextUp(nextUp(pc))}
        //
        // and we test that log1p(pcNeighbors[i]) <= log1p(pcNeighbors[i+1])
        {
            double pcNeighbors[] = new double[5];
            double pcNeighborsLog1p[] = new double[5];
            double pcNeighborsStrictLog1p[] = new double[5];

            for(int i = -36; i <= 36; i++) {
                double pc = StrictMath.pow(Math.E, i) - 1;

                pcNeighbors[2] = pc;
                pcNeighbors[1] = FpUtils.nextDown(pc);
                pcNeighbors[0] = FpUtils.nextDown(pcNeighbors[1]);
                pcNeighbors[3] = FpUtils.nextUp(pc);
                pcNeighbors[4] = FpUtils.nextUp(pcNeighbors[3]);

                for(int j = 0; j < pcNeighbors.length; j++) {
                    pcNeighborsLog1p[j]       =       Math.log1p(pcNeighbors[j]);
                    pcNeighborsStrictLog1p[j] = StrictMath.log1p(pcNeighbors[j]);
                }

                for(int j = 0; j < pcNeighborsLog1p.length-1; j++) {
                    if(pcNeighborsLog1p[j] >  pcNeighborsLog1p[j+1] ) {
                        failures++;
                        System.err.println("Monotonicity failure for Math.log1p on " +
                                          pcNeighbors[j] + " and "  +
                                          pcNeighbors[j+1] + "\n\treturned " +
                                          pcNeighborsLog1p[j] + " and " +
                                          pcNeighborsLog1p[j+1] );
                    }

                    if(pcNeighborsStrictLog1p[j] >  pcNeighborsStrictLog1p[j+1] ) {
                        failures++;
                        System.err.println("Monotonicity failure for StrictMath.log1p on " +
                                          pcNeighbors[j] + " and "  +
                                          pcNeighbors[j+1] + "\n\treturned " +
                                          pcNeighborsStrictLog1p[j] + " and " +
                                          pcNeighborsStrictLog1p[j+1] );
                    }


                }

            }
        }

        return failures;
    }

    public static int testLog1pCase(double input,
                                    double expected) {
        return testLog1pCaseWithUlpDiff(input, expected, 1);
    }

    public static int testLog1pCaseWithUlpDiff(double input,
                                               double expected,
                                               double ulps) {
        int failures = 0;
        failures += Tests.testUlpDiff("Math.lop1p(double",
                                      input, Math.log1p(input),
                                      expected, ulps);
        failures += Tests.testUlpDiff("StrictMath.log1p(double",
                                      input, StrictMath.log1p(input),
                                      expected, ulps);
        return failures;
    }

    public static void main(String argv[]) {
        int failures = 0;

        failures += testLog1p();

        if (failures > 0) {
            System.err.println("Testing log1p incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

}
