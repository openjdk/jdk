/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.*;
import jdk.internal.math.DoubleConsts;
import jdk.internal.math.FloatConsts;

/*
 * @test
 * @bug 6506405 8241374
 * @summary Test abs and absExact for Math and StrictMath
 * @modules java.base/jdk.internal.math
 */
public class AbsTests {
    private static final float  EULER_F   = (float)Math.exp(1.0);
    private static final float  GELFOND_F = (float)Math.exp(Math.PI);
    private static final float  PI_F      = (float)Math.PI;
    private static final float  TAU_F     = 2.0F*PI_F;

    private static final double EULER_D   = Math.exp(1.0);
    private static final double GELFOND_D = Math.exp(Math.PI);
    private static final double PI_D      = Math.PI;
    private static final double TAU_D     = 2.0*PI_D;

    private static int errors = 0;

    public static void main(String... args) {
        errors += testInRangeIntAbs();
        errors += testIntMinValue();
        errors += testInRangeLongAbs();
        errors += testLongMinValue();
        errors += testInRangeFloatAbs();
        errors += testInRangeDoubleAbs();

        if (errors > 0) {
            throw new RuntimeException(errors + " errors found testing abs.");
        }
    }

    // --------------------------------------------------------------------

    private static int testInRangeIntAbs() {
        int errors = 0;
        int[][] testCases  = {
            // Argument to abs, expected result
            {+0, 0},
            {+1, 1},
            {-1, 1},
            {-2, 2},
            {+2, 2},
            {-Integer.MAX_VALUE, Integer.MAX_VALUE},
            {+Integer.MAX_VALUE, Integer.MAX_VALUE}
        };

        for(var testCase : testCases) {
            errors += testIntAbs(Math::abs,      testCase[0], testCase[1]);
            errors += testIntAbs(Math::absExact, testCase[0], testCase[1]);
        }
        return errors;
    }

    private static int testIntMinValue() {
        int errors = 0;
        // Strange but true
        errors += testIntAbs(Math::abs, Integer.MIN_VALUE, Integer.MIN_VALUE);

        // Test exceptional behavior for absExact
        try {
            int result = Math.absExact(Integer.MIN_VALUE);
            System.err.printf("Bad return value %d from Math.absExact(MIN_VALUE)%n",
                              result);
            errors++;
        } catch (ArithmeticException ae) {
            ; // Expected
        }
        return errors;
    }

    private static int testIntAbs(IntUnaryOperator absFunc,
                           int argument, int expected) {
        int result = absFunc.applyAsInt(argument);
        if (result != expected) {
            System.err.printf("Unexpected int abs result %d for argument %d%n",
                                result, argument);
            return 1;
        } else {
            return 0;
        }
    }

    // --------------------------------------------------------------------

    private static long testInRangeLongAbs() {
        int errors = 0;
        long[][] testCases  = {
            // Argument to abs, expected result
            {+0L, 0L},
            {+1L, 1L},
            {-1L, 1L},
            {-2L, 2L},
            {+2L, 2L},
            {-Integer.MAX_VALUE, Integer.MAX_VALUE},
            {+Integer.MAX_VALUE, Integer.MAX_VALUE},
            { Integer.MIN_VALUE, -((long)Integer.MIN_VALUE)},
            {-Long.MAX_VALUE, Long.MAX_VALUE},
        };

        for(var testCase : testCases) {
            errors += testLongAbs(Math::abs,      testCase[0], testCase[1]);
            errors += testLongAbs(Math::absExact, testCase[0], testCase[1]);
        }
        return errors;
    }

    private static int testLongMinValue() {
        int errors = 0;
        // Strange but true
        errors += testLongAbs(Math::abs, Long.MIN_VALUE, Long.MIN_VALUE);

        // Test exceptional behavior for absExact
        try {
            long result = Math.absExact(Long.MIN_VALUE);
            System.err.printf("Bad return value %d from Math.absExact(MIN_VALUE)%n",
                              result);
            errors++;
        } catch (ArithmeticException ae) {
            ; // Expected
        }
        return errors;
    }

    private static int testLongAbs(LongUnaryOperator absFunc,
                           long argument, long expected) {
        long result = absFunc.applyAsLong(argument);
        if (result != expected) {
            System.err.printf("Unexpected long abs result %d for argument %d%n",
                                result, argument);
            return 1;
        } else {
            return 0;
        }
    }

    // --------------------------------------------------------------------

    private static float testInRangeFloatAbs() {
        int errors = 0;
        float[][] testCases  = {
            // Argument to abs, expected result
            {+0.0F, 0.0F},
            {-0.0F, 0.0F},
            {-Float.MIN_VALUE, Float.MIN_VALUE},
            {-Float.MIN_NORMAL, Float.MIN_NORMAL},
            {Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY},
            {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY},
            {Float.intBitsToFloat(FloatConsts.SIGN_BIT_MASK |
                (1 << FloatConsts.SIGNIFICAND_WIDTH) |
               ((1 << FloatConsts.SIGNIFICAND_WIDTH) - 1)),
             Float.intBitsToFloat((1 << FloatConsts.SIGNIFICAND_WIDTH) |
               ((1 << FloatConsts.SIGNIFICAND_WIDTH) - 1))},
            {FloatConsts.SIGN_BIT_MASK | (FloatConsts.MAG_BIT_MASK >>> 1),
                FloatConsts.MAG_BIT_MASK >>> 1},
            {-EULER_F, EULER_F},
            {-GELFOND_F, GELFOND_F},
            {-PI_F, PI_F},
            {-TAU_F, TAU_F}
        };

        for(var testCase : testCases) {
            errors += testFloatAbs(Math::abs,      testCase[0], testCase[1]);
        }
        return errors;
    }

    private static int testFloatAbs(UnaryOperator<Float> absFunc,
                           float argument, float expected) {
        float result = absFunc.apply(argument);
        if (result != expected) {
            System.err.printf("Unexpected float abs result %f for argument %f%n",
                                result, argument);
            return 1;
        } else {
            return 0;
        }
    }

    // --------------------------------------------------------------------

    private static double testInRangeDoubleAbs() {
        int errors = 0;
        double[][] testCases  = {
            // Argument to abs, expected result
            {+0.0, 0.0},
            {-0.0, 0.0},
            {-Double.MIN_VALUE, Double.MIN_VALUE},
            {-Double.MIN_NORMAL, Double.MIN_NORMAL},
            {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY},
            {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY},
            {Double.longBitsToDouble(DoubleConsts.SIGN_BIT_MASK |
                (1 << DoubleConsts.SIGNIFICAND_WIDTH) |
               ((1 << DoubleConsts.SIGNIFICAND_WIDTH) - 1)),
             Double.longBitsToDouble((1 << DoubleConsts.SIGNIFICAND_WIDTH) |
               ((1 << DoubleConsts.SIGNIFICAND_WIDTH) - 1))},
            {DoubleConsts.SIGN_BIT_MASK | (DoubleConsts.MAG_BIT_MASK >>> 1),
                DoubleConsts.MAG_BIT_MASK >>> 1},
            {-EULER_D, EULER_D},
            {-GELFOND_D, GELFOND_D},
            {-PI_D, PI_D},
            {-TAU_D, TAU_D}
        };

        for(var testCase : testCases) {
            errors += testDoubleAbs(Math::abs,      testCase[0], testCase[1]);
        }
        return errors;
    }

    private static int testDoubleAbs(DoubleUnaryOperator absFunc,
                           double argument, double expected) {
        double result = absFunc.applyAsDouble(argument);
        if (result != expected) {
            System.err.printf("Unexpected double abs result %f for argument %f%n",
                                result, argument);
            return 1;
        } else {
            return 0;
        }
    }
}
