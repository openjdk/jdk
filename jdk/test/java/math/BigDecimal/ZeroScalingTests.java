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
 * @bug 4902952 4905407 4916149
 * @summary Tests that the scale of zero is propagated properly and has the proper effect.
 * @author Joseph D. Darcy
 * @compile -source 1.5 ZeroScalingTests.java
 * @run main ZeroScalingTests
 */

import java.math.*;
import java.util.*;

public class ZeroScalingTests {

    static MathContext longEnough = new MathContext(50, RoundingMode.UNNECESSARY);

    static BigDecimal[]  zeros = new BigDecimal[23];
    static {
        for(int i = 0; i < 21; i++) {
            zeros[i] = new BigDecimal(BigInteger.ZERO, i-10);
        }
        zeros[21] = new BigDecimal(BigInteger.ZERO, Integer.MIN_VALUE);
        zeros[22] = new BigDecimal(BigInteger.ZERO, Integer.MAX_VALUE);
    }

    static BigDecimal element = BigDecimal.valueOf(100, -2);

    static MathContext contexts[] = {
        new MathContext(0, RoundingMode.UNNECESSARY),
        new MathContext(100, RoundingMode.UNNECESSARY),
        new MathContext(5, RoundingMode.UNNECESSARY),
        new MathContext(4, RoundingMode.UNNECESSARY),
        new MathContext(3, RoundingMode.UNNECESSARY),
        new MathContext(2, RoundingMode.UNNECESSARY),
        new MathContext(1, RoundingMode.UNNECESSARY),
    };


    static int addTests() {
        int failures = 0;

        for(BigDecimal zero1: zeros) {
            for(BigDecimal zero2: zeros) {
                BigDecimal expected = new BigDecimal(BigInteger.ZERO,
                                                     Math.max(zero1.scale(), zero2.scale()));
                BigDecimal result;

                if(! (result=zero1.add(zero2)).equals(expected) ) {
                    failures++;
                    System.err.println("For classic exact add, expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

                if(! (result=zero1.add(zero2, MathContext.UNLIMITED)).equals(expected) ) {
                    failures++;
                    System.err.println("For UNLIMITED math context add," +
                                       " expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

                if(! (result=zero1.add(zero2, longEnough)).equals(expected) ) {
                    failures++;
                    System.err.println("For longEnough math context add," +
                                       " expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

            }
        }

        // Test effect of adding zero to a nonzero value.
        for (MathContext mc: contexts) {
            for (BigDecimal zero: zeros) {
                if (Math.abs((long)zero.scale()) < 100 ) {

                    int preferredScale = Math.max(zero.scale(), element.scale());
                    if (mc.getPrecision() != 0) {
                        if (preferredScale < -4 )
                            preferredScale = -4;
                        else if (preferredScale > -(5 - mc.getPrecision())) {
                            preferredScale = -(5 - mc.getPrecision());
                        }
                    }


                    /*
                      System.err.println("\n    " + element + " +\t" + zero + " =\t" + result);

                      System.err.println("scales" + element.scale() + " \t" + zero.scale() +
                      "  \t " + result.scale() + "\t precison = " + mc.getPrecision());
                      System.err.println("expected scale = " + preferredScale);
                    */

                    BigDecimal result = element.add(zero, mc);
                    if (result.scale() != preferredScale ||
                            result.compareTo(element) != 0) {
                        failures++;
                        System.err.println("Expected scale  " + preferredScale +
                                           " result scale was " + result.scale() +
                                           " ; value was " + result);
                    }

                    result = zero.add(element, mc);
                    if (result.scale() != preferredScale ||
                            result.compareTo(element) != 0) {
                        failures++;
                        System.err.println("Expected scale  " + preferredScale +
                                           " result scale was " + result.scale() +
                                           " ; value was " + result);
                    }

                    result = element.negate().add(zero, mc);
                    if (result.scale() != preferredScale ||
                            result.compareTo(element.negate()) != 0) {
                        failures++;
                        System.err.println("Expected scale  " + preferredScale +
                                           " result scale was " + result.scale() +
                                           " ; value was " + result);
                    }

                    result = zero.add(element.negate(), mc);
                    if (result.scale() != preferredScale ||
                            result.compareTo(element.negate()) != 0) {
                        failures++;
                        System.err.println("Expected scale  " + preferredScale +
                                           " result scale was " + result.scale() +
                                           " ; value was " + result);
                    }

                }
            }
        }

        return failures;
    }

    static int subtractTests() {
        int failures = 0;

        for(BigDecimal zero1: zeros) {
            for(BigDecimal zero2: zeros) {
                BigDecimal expected = new BigDecimal(BigInteger.ZERO,
                                                     Math.max(zero1.scale(), zero2.scale()));
                BigDecimal result;

                if(! (result=zero1.subtract(zero2)).equals(expected) ) {
                    failures++;
                    System.err.println("For classic exact subtract, expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

                if(! (result=zero1.subtract(zero2, MathContext.UNLIMITED)).equals(expected) ) {
                    failures++;
                    System.err.println("For UNLIMITED math context subtract," +
                                       " expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

                if(! (result=zero1.subtract(zero2, longEnough)).equals(expected) ) {
                    failures++;
                    System.err.println("For longEnough math context subtract," +
                                       " expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

            }
        }


        // Test effect of adding zero to a nonzero value.
        for (MathContext mc: contexts) {
            for (BigDecimal zero: zeros) {
                if (Math.abs((long)zero.scale()) < 100 ) {

                    int preferredScale = Math.max(zero.scale(), element.scale());
                    if (mc.getPrecision() != 0) {
                        if (preferredScale < -4 )
                            preferredScale = -4;
                        else if (preferredScale > -(5 - mc.getPrecision())) {
                            preferredScale = -(5 - mc.getPrecision());
                        }
                    }


                    /*
                      System.err.println("\n    " + element + " +\t" + zero + " =\t" + result);

                      System.err.println("scales" + element.scale() + " \t" + zero.scale() +
                      "  \t " + result.scale() + "\t precison = " + mc.getPrecision());
                      System.err.println("expected scale = " + preferredScale);
                    */

                    BigDecimal result = element.subtract(zero, mc);
                    if (result.scale() != preferredScale ||
                            result.compareTo(element) != 0) {
                        failures++;
                        System.err.println("Expected scale  " + preferredScale +
                                           " result scale was " + result.scale() +
                                           " ; value was " + result);
                    }

                    result = zero.subtract(element, mc);
                    if (result.scale() != preferredScale ||
                            result.compareTo(element.negate()) != 0) {
                        failures++;
                        System.err.println("Expected scale  " + preferredScale +
                                           " result scale was " + result.scale() +
                                           " ; value was " + result);
                    }

                    result = element.negate().subtract(zero, mc);
                    if (result.scale() != preferredScale ||
                            result.compareTo(element.negate()) != 0) {
                        failures++;
                        System.err.println("Expected scale  " + preferredScale +
                                           " result scale was " + result.scale() +
                                           " ; value was " + result);
                    }

                    result = zero.subtract(element.negate(), mc);
                    if (result.scale() != preferredScale ||
                            result.compareTo(element) != 0) {
                        failures++;
                        System.err.println("Expected scale  " + preferredScale +
                                           " result scale was " + result.scale() +
                                           " ; value was " + result);
                    }

                }
            }
        }

        return failures;
    }

    static int multiplyTests() {
        int failures = 0;

        BigDecimal ones[] = {
            BigDecimal.valueOf(1, 0),
            BigDecimal.valueOf(10, 1),
            BigDecimal.valueOf(1000, 3),
            BigDecimal.valueOf(100000000, 8),
        };

        List<BigDecimal> values = new LinkedList<BigDecimal>();
        values.addAll(Arrays.asList(zeros));
        values.addAll(Arrays.asList(ones));

        for(BigDecimal zero1: zeros) {
            for(BigDecimal value: values) {
                BigDecimal expected = new BigDecimal(BigInteger.ZERO,
                                                     (int)Math.min(Math.max((long)zero1.scale()+value.scale(),
                                                                            Integer.MIN_VALUE ),
                                                                   Integer.MAX_VALUE ) );
                BigDecimal result;

                if(! (result=zero1.multiply(value)).equals(expected) ) {
                    failures++;
                    System.err.println("For classic exact multiply, expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

                if(! (result=zero1.multiply(value, MathContext.UNLIMITED)).equals(expected) ) {
                    failures++;
                    System.err.println("For UNLIMITED math context multiply," +
                                       " expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

                if(! (result=zero1.multiply(value, longEnough)).equals(expected) ) {
                    failures++;
                    System.err.println("For longEnough math context multiply," +
                                       " expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

            }
        }

        return failures;
    }

    static int divideTests() {
        int failures = 0;

        BigDecimal [] ones = {
            BigDecimal.valueOf(1, 0),
            BigDecimal.valueOf(10, -1),
            BigDecimal.valueOf(100, -2),
            BigDecimal.valueOf(1000, -3),
            BigDecimal.valueOf(1000000, -5),
        };

        for(BigDecimal one: ones) {
            for(BigDecimal zero: zeros) {
                BigDecimal expected = new BigDecimal(BigInteger.ZERO,
                                                     (int)Math.min(Math.max((long)zero.scale() - one.scale(),
                                                                            Integer.MIN_VALUE ),
                                                                   Integer.MAX_VALUE ) );
                BigDecimal result;

                if(! (result=zero.divide(one)).equals(expected) ) {
                    failures++;
                    System.err.println("For classic exact divide, expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

                if(! (result=zero.divide(one, MathContext.UNLIMITED)).equals(expected) ) {
                    failures++;
                    System.err.println("For UNLIMITED math context divide," +
                                       " expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

                if(! (result=zero.divide(one, longEnough)).equals(expected) ) {
                    failures++;
                    System.err.println("For longEnough math context divide," +
                                       " expected scale of " +
                                       expected.scale() + "; got " +
                                       result.scale() + ".");
                }

            }
        }

        return failures;
    }

    static int setScaleTests() {
        int failures = 0;

        int scales[] = {
            Integer.MIN_VALUE,
            Integer.MIN_VALUE+1,
            -10000000,
            -3,
            -2,
            -1,
            0,
            1,
            2,
            3,
            10,
            10000000,
            Integer.MAX_VALUE-1,
            Integer.MAX_VALUE
        };

        for(BigDecimal zero: zeros) {
            for(int scale: scales) {
                try {
                    BigDecimal bd = zero.setScale(scale);
                }
                catch (ArithmeticException e) {
                    failures++;
                    System.err.println("Exception when trying to set a scale of " + scale +
                                       " on " + zero);
                }
            }
        }

        return failures;
    }

    static int toEngineeringStringTests() {
        int failures = 0;

        String [][] testCases  = {
            {"0E+10",   "0.00E+12"},
            {"0E+9",    "0E+9"},
            {"0E+8",    "0.0E+9"},
            {"0E+7",    "0.00E+9"},

            {"0E-10",   "0.0E-9"},
            {"0E-9",    "0E-9"},
            {"0E-8",    "0.00E-6"},
            {"0E-7",    "0.0E-6"},
        };

        for(String[] testCase: testCases) {
            BigDecimal bd = new BigDecimal(testCase[0]);
            String result = bd.toEngineeringString();

            if (!result.equals(testCase[1]) ||
                !bd.equals(new BigDecimal(result))) {
                failures++;
                System.err.println("From input ``" + testCase[0] + ",'' " +
                                   " bad engineering string output ``" + result +
                                   "''; expected ``" + testCase[1] + ".''");
            }

        }

        return failures;
    }

    static int ulpTests() {
        int failures = 0;

        for(BigDecimal zero: zeros) {
            BigDecimal result;
            BigDecimal expected = BigDecimal.valueOf(1, zero.scale());

            if (! (result=zero.ulp()).equals(expected) ) {
                failures++;
                System.err.println("Unexpected ulp value for zero value " +
                                   zero + "; expected " + expected +
                                   ", got " + result);
            }
        }

        return failures;
    }

    public static void main(String argv[]) {
        int failures = 0;

        failures += addTests();
        failures += subtractTests();
        failures += multiplyTests();
        failures += divideTests();
        failures += setScaleTests();
        failures += toEngineeringStringTests();
        failures += ulpTests();

        if (failures > 0 ) {
            throw new RuntimeException("Incurred " + failures + " failures" +
                                       " testing the preservation of zero scales.");
        }
    }
}
