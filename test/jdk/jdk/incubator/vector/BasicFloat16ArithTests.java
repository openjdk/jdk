/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8329817 8334432 8339076 8341260 8362207
 * @modules jdk.incubator.vector
 * @summary Basic tests of Float16 arithmetic and similar operations
 */

import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.*;
import java.util.HashSet;
import java.util.List;

public class BasicFloat16ArithTests {
    private static float InfinityF = Float.POSITIVE_INFINITY;
    private static float NaNf = Float.NaN;

    private static final float MAX_VAL_FP16 = 0x1.ffcp15f;

    public static void main(String... args) {
        checkBitWise();
        checkHash();
        checkConstants();
        checkNegate();
        checkAbs();
        checkIsNaN();
        checkFiniteness();
        checkMinMax();
        checkArith();
        checkSqrt();
        checkGetExponent();
        checkUlp();
        checkValueOfDouble();
        checkValueOfLong();
        checkValueOfString();
        checkBaseConversionRoundTrip();
        FusedMultiplyAddTests.main();
    }

    /*
     * The software implementation of Float16 delegates to float or
     * double operations for most of the actual computation. This
     * regression test takes that into account as it generally only
     * has limited testing to probe whether or not the proper
     * functionality is being delegated to.
     *
     * To make the test easier to read, float literals that are exact
     * upon conversion to Float16 are used for the test data.
     *
     * The float <-> Float16 conversions are well-tested from prior
     * work and are assumed to be correct by this regression test.
     */

    /**
     * Verify handling of NaN representations
     */
    private static void checkBitWise() {
        short nanImage = float16ToRawShortBits(Float16.NaN);

        int exponent = 0x7c00;
        int sign =     0x8000;

        // All-zeros significand with a max exponent are infinite
        // values, not NaN values.
        for(int i = 0x1; i <= 0x03ff; i++) {
            short  posNaNasShort = (short)(       exponent | i);
            short  negNaNasShort = (short)(sign | exponent | i);

            Float16 posf16 = shortBitsToFloat16(posNaNasShort);
            Float16 negf16 = shortBitsToFloat16(negNaNasShort);

            // Mask-off high-order 16 bits to avoid sign extension woes
            checkInt(nanImage & 0xffff, float16ToShortBits(posf16) & 0xffff, "positive NaN");
            checkInt(nanImage & 0xffff, float16ToShortBits(negf16) & 0xffff, "negative NaN");

            checkInt(posNaNasShort & 0xffff, float16ToRawShortBits(posf16) & 0xffff , "positive NaN");
            checkInt(negNaNasShort & 0xffff, float16ToRawShortBits(negf16) & 0xffff, "negative NaN");
        }
    }

    /**
     * Verify correct number of hashValue's from Float16's.
     */
    private static void checkHash() {
        // Slightly over-allocate the HashSet.
        HashSet<Integer> set = HashSet.newHashSet(Short.MAX_VALUE - Short.MIN_VALUE + 1);

        // Each non-NaN value should have a distinct hashCode. All NaN
        // values should share a single hashCode. Check the latter
        // property by verifying the overall count of entries in the
        // set.
        for(int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Float16 f16 = Float16.shortBitsToFloat16((short)i);
            boolean addedToSet = set.add(f16.hashCode());

            if (!Float16.isNaN(f16)) {
                if (!addedToSet) {
                    throwRE("Existing hash value for " + f16);
                }
            }
        }

        // There are 2^16 = 65,536 total short values. Each of these
        // bit patterns is a valid representation of a Float16
        // value. However, NaNs have multiple possible encodings.
        // With an exponent = 0x7c00, each nonzero significand 0x1 to
        // 0x3ff is a NaN, for both positive and negative sign bits.
        //
        // Therefore, the total number of distinct hash codes for
        // Float16 values should be:
        // 65_536 - 2*(1_023) + 1 = 63_491

        int setSize = set.size();
        if (setSize != 63_491) {
            throwRE("Unexpected number of distinct hash values " + setSize);
        }
    }

    private static void checkConstants() {
        checkInt(BYTES,          2, "Float16.BYTES");
        checkInt(MAX_EXPONENT,  15, "Float16.MAX_EXPONENT");
        checkInt(MIN_EXPONENT, -14, "Float16.MIN_EXPONENT");
        checkInt(PRECISION,     11, "Float16.PRECISION");
        checkInt(SIZE,          16, "Float16.SIZE");

        checkFloat16(MIN_VALUE,  0x1.0p-24f, "Float16.MIN_VALUE");
        checkFloat16(MIN_NORMAL, 0x1.0p-14f, "Float16.MIN_NORMAL");
        checkFloat16(MAX_VALUE,  65504.0f,  "Float16.MAX_VALUE");

        checkFloat16(POSITIVE_INFINITY,   InfinityF,  "+infinity");
        checkFloat16(NEGATIVE_INFINITY,  -InfinityF,  "-infinity");
        checkFloat16(NaN,                 NaNf,            "NaN");
    }

    private static void checkInt(int value, int expected, String message) {
        if (value != expected) {
            throwRE(String.format("Didn't get expected value for %s;%nexpected %d, got %d",
                                  message, expected, value));
        }
    }

    private static void checkFloat16(Float16 value16, float expected, String message) {
        float value = value16.floatValue();
        if (Float.compare(value, expected) != 0) {
            throwRE(String.format("Didn't get expected value for %s;%nexpected %g (%a), got %g (%a)",
                                  message, expected, expected, value, value));
        }
    }

    private static void checkNegate() {
        float[][] testCases = {
            {-0.0f,   0.0f},
            { 0.0f,  -0.0f},

            {-1.0f,   1.0f},
            { 1.0f,  -1.0f},

            { InfinityF, -InfinityF},
            {-InfinityF,  InfinityF},

            {NaNf,       NaNf},
        };

        for(var testCase : testCases) {
            float arg =      testCase[0];
            float expected = testCase[1];
            Float16 result =  negate(valueOfExact(arg));

            if (Float.compare(expected, result.floatValue()) != 0) {
                checkFloat16(result, expected, "negate(" + arg + ")");
            }
        }

        return;
    }

    private static void checkAbs() {
        float[][] testCases = {
            {-0.0f,   0.0f},
            { 0.0f,   0.0f},

            {-1.0f,   1.0f},
            { 1.0f,   1.0f},

            { InfinityF, InfinityF},
            {-InfinityF, InfinityF},

            {NaNf,       NaNf},
        };

        for(var testCase : testCases) {
            float arg =      testCase[0];
            float expected = testCase[1];
            Float16 result =  abs(valueOfExact(arg));

            if (Float.compare(expected, result.floatValue()) != 0) {
                checkFloat16(result, expected, "abs(" + arg + ")");
            }
        }

        return;
    }

    private static void checkIsNaN() {
        if (!isNaN(NaN)) {
            throwRE("Float16.isNaN() returns false for a NaN");
        }

        float[] testCases = {
            -InfinityF,
             InfinityF,
            -0.0f,
            +0.0f,
             1.0f,
            -1.0f,
        };

        for(var testCase : testCases) {
            boolean result = isNaN(valueOfExact(testCase));
            if (result) {
                throwRE("isNaN returned true for " + testCase);
            }
        }

        return;
    }

    private static void checkFiniteness() {
        float[] infinities = {
            -InfinityF,
             InfinityF,
        };

        for(var infinity : infinities) {
            boolean result1 = isFinite(valueOfExact(infinity));
            boolean result2 = isInfinite(valueOfExact(infinity));

            if (result1) {
                throwRE("Float16.isFinite returned true for " + infinity);
            }

            if (!result2) {
                throwRE("Float16.isInfinite returned false for " + infinity);
            }
        }

        if (isFinite(NaN)) {
            throwRE("Float16.isFinite() returns true for a NaN");
        }

        if (isInfinite(NaN)) {
            throwRE("Float16.isInfinite() returns true for a NaN");
        }

        float[] finities = {
            -0.0f,
            +0.0f,
             1.0f,
            -1.0f,
        };

        for(var finity : finities) {
            boolean result1 = isFinite(valueOfExact(finity));
            boolean result2 = isInfinite(valueOfExact(finity));

            if (!result1) {
                throwRE("Float16.isFinite returned true for " + finity);
            }

            if (result2) {
                throwRE("Float16.isInfinite returned true for " + finity);
            }
        }

        return;
    }

    private static void checkMinMax() {
        float small = 1.0f;
        float large = 2.0f;

        if (min(valueOfExact(small), valueOfExact(large)).floatValue() != small) {
            throwRE(String.format("min(%g, %g) not equal to %g)",
                                  small, large, small));
        }

        if (max(valueOfExact(small), valueOfExact(large)).floatValue() != large) {
            throwRE(String.format("max(%g, %g) not equal to %g)",
                                  small, large, large));
        }
    }

    /*
     * Cursory checks to make sure correct operation is being called
     * with arguments in proper order.
     */
    private static void checkArith() {
        float   a   = 1.0f;
        Float16 a16 = valueOfExact(a);

        float   b   = 2.0f;
        Float16 b16 = valueOfExact(b);

        if (add(a16, b16).floatValue() != (a + b)) {
            throwRE("failure with " + a16 + " + " + b16);
        }
        if (add(b16, a16).floatValue() != (b + a)) {
            throwRE("failure with " + b16 + " + " + a16);
        }

        if (subtract(a16, b16).floatValue() != (a - b)) {
            throwRE("failure with " + a16 + " - " + b16);
        }
        if (subtract(b16, a16).floatValue() != (b - a)) {
            throwRE("failure with " + b16 + " - " + a16);
        }

        if (multiply(a16, b16).floatValue() != (a * b)) {
            throwRE("failure with " + a16 + " * " + b16);
        }
        if (multiply(b16, a16).floatValue() != (b * a)) {
            throwRE("failure with " + b16 + " * " + a16);
        }

        if (divide(a16, b16).floatValue() != (a / b)) {
            throwRE("failure with " + a16 + " / " + b16);
        }
        if (divide(b16, a16).floatValue() != (b / a)) {
            throwRE("failure with " + b16 + " / " + a16);
        }
        return;
    }

    private static void checkSqrt() {
        float[][] testCases = {
            {-0.0f,   -0.0f},
            { 0.0f,    0.0f},

            {1.0f,   1.0f},
            {4.0f,   2.0f},
            {9.0f,   3.0f},

            { InfinityF, InfinityF},
            {-InfinityF, NaNf},

            {NaNf,       NaNf},
        };

        for(var testCase : testCases) {
            float arg =      testCase[0];
            float expected = testCase[1];
            Float16 result =  sqrt(valueOfExact(arg));

            if (Float.compare(expected, result.floatValue()) != 0) {
                checkFloat16(result, expected, "sqrt(" + arg + ")");
            }
        }

        return;
    }

    private static void checkGetExponent() {
        float[][] testCases = {
            // Non-finite values
            { InfinityF, MAX_EXPONENT + 1},
            {-InfinityF, MAX_EXPONENT + 1},
            { NaNf,      MAX_EXPONENT + 1},

            // Subnormal and almost subnormal values
            {-0.0f,       MIN_EXPONENT - 1},
            {+0.0f,       MIN_EXPONENT - 1},
            { 0x1.0p-24f, MIN_EXPONENT - 1}, // Float16.MIN_VALUE
            {-0x1.0p-24f, MIN_EXPONENT - 1}, // Float16.MIN_VALUE
            { 0x1.0p-14f, MIN_EXPONENT},     // Float16.MIN_NORMAL
            {-0x1.0p-14f, MIN_EXPONENT},     // Float16.MIN_NORMAL

            // Normal values
            { 1.0f,       0},
            { 2.0f,       1},
            { 4.0f,       2},

            {MAX_VAL_FP16*0.5f, MAX_EXPONENT - 1},
            {MAX_VAL_FP16,      MAX_EXPONENT},
        };

        for(var testCase : testCases) {
            float arg =      testCase[0];
            float expected = testCase[1];
            // Exponents are in-range for Float16
            Float16 result =  valueOfExact(getExponent(valueOfExact(arg)));

            if (Float.compare(expected, result.floatValue()) != 0) {
                checkFloat16(result, expected, "getExponent(" + arg + ")");
            }
        }
        return;
    }

    private static void checkUlp() {
        float[][] testCases = {
            { InfinityF, InfinityF},
            {-InfinityF, InfinityF},
            { NaNf,      NaNf},

            // Zeros, subnormals, and MIN_VALUE all have MIN_VALUE as an ulp.
            {-0.0f,       0x1.0p-24f},
            {+0.0f,       0x1.0p-24f},
            { 0x1.0p-24f, 0x1.0p-24f},
            {-0x1.0p-24f, 0x1.0p-24f},
            { 0x1.0p-14f, 0x1.0p-24f},
            {-0x1.0p-14f, 0x1.0p-24f},

            // ulp is 10 bits away
            {0x1.0p0f,       0x0.004p0f}, // 1.0f
            {0x1.0p1f,       0x0.004p1f}, // 2.0f
            {0x1.0p2f,       0x0.004p2f}, // 4.0f

            {MAX_VAL_FP16*0.5f, 0x0.004p14f},
            {MAX_VAL_FP16,      0x0.004p15f},
        };

        for(var testCase : testCases) {
            float arg =      testCase[0];
            float expected = testCase[1];
            // Exponents are in-range for Float16
            Float16 result =  ulp(valueOfExact(arg));

            if (Float.compare(expected, result.floatValue()) != 0) {
                checkFloat16(result, expected, "ulp(" + arg + ")");
            }
        }
        return;
    }

    private static void throwRE(String message) {
        throw new RuntimeException(message);
    }

    private static void checkValueOfDouble() {
        /*
         * Check that double -> Float16 conversion rounds properly
         * around the midway point for each finite Float16 value by
         * looping over the positive values and checking the negations
         * along the way.
         */

        String roundUpMsg   = "Didn't get half-way case rounding down";
        String roundDownMsg = "Didn't get half-way case rounding up";

        for(int i = 0; i <= Short.MAX_VALUE; i++ ) {
            boolean isEven = ((i & 0x1) == 0);
            Float16 f16 = Float16.shortBitsToFloat16((short)i);
            Float16 f16Neg = negate(f16);

            if (!isFinite(f16))
                continue;

            // System.out.println("\t" + toHexString(f16));

            Float16 ulp = ulp(f16);
            double halfWay = f16.doubleValue() + ulp.doubleValue() * 0.5;

            // Under the round to nearest even rounding policy, the
            // half-way case should round down to the starting value
            // if the starting value is even; otherwise, it should round up.
            float roundedBack = valueOf(halfWay).floatValue();
            float roundedBackNeg = valueOf(-halfWay).floatValue();

            if (isEven) {
                checkFloat16(f16,    roundedBack,    roundDownMsg);
                checkFloat16(f16Neg, roundedBackNeg, roundDownMsg);
            } else {
                checkFloat16(add(f16,         ulp), roundedBack,    roundUpMsg);
                checkFloat16(subtract(f16Neg, ulp), roundedBackNeg, roundUpMsg);
            }

            // Should always round down
            double halfWayNextDown = Math.nextDown(halfWay);
            checkFloat16(f16,    valueOf(halfWayNextDown).floatValue(),  roundDownMsg);
            checkFloat16(f16Neg, valueOf(-halfWayNextDown).floatValue(), roundDownMsg);

            // Should always round up
            double halfWayNextUp =   Math.nextUp(halfWay);
            checkFloat16(add(f16, ulp),         valueOf( halfWayNextUp).floatValue(), roundUpMsg);
            checkFloat16(subtract(f16Neg, ulp), valueOf(-halfWayNextUp).floatValue(), roundUpMsg);
        }
    }

    private static void checkValueOfLong() {
        checkFloat16(valueOf(-65_521),  Float.NEGATIVE_INFINITY, "-infinity");
        checkFloat16(valueOf(-65_520),  Float.NEGATIVE_INFINITY, "-infinity");
        checkFloat16(valueOf(-65_519), -MAX_VALUE.floatValue(), "-MAX_VALUE");
        checkFloat16(valueOf(65_519),   MAX_VALUE.floatValue(), "MAX_VALUE");
        checkFloat16(valueOf(65_520),   Float.POSITIVE_INFINITY, "+infinity");
        checkFloat16(valueOf(65_521),   Float.POSITIVE_INFINITY, "+infinity");
    }

    private static void checkValueOfString() {
        String2Float16Case[] testCases = {
            new String2Float16Case( "NaN", NaNf),
            new String2Float16Case("+NaN", NaNf),
            new String2Float16Case("-NaN", NaNf),

            new String2Float16Case("+Infinity", +InfinityF),
            new String2Float16Case("-Infinity", -InfinityF),

            new String2Float16Case( "0.0",  0.0f),
            new String2Float16Case("+0.0",  0.0f),
            new String2Float16Case("-0.0", -0.0f),

            // Decimal signed integers are accepted as input; hex
            // signed integers are not, see negative test cases below.
            new String2Float16Case( "1",  1.0f),
            new String2Float16Case("-1", -1.0f),

            new String2Float16Case( "12",  12.0f),
            new String2Float16Case("-12", -12.0f),

            new String2Float16Case( "123",  123.0f),
            new String2Float16Case("-123", -123.0f),

            new String2Float16Case( "1.0",  1.0f),
            new String2Float16Case("-1.0", -1.0f),

            // Check for FloatTypeSuffix handling
            new String2Float16Case( "1.5f", 1.5f),
            new String2Float16Case( "1.5F", 1.5f),
            new String2Float16Case( "1.5D", 1.5f),
            new String2Float16Case( "1.5d", 1.5f),

            new String2Float16Case("65504.0", 65504.0f),  // Float16.MAX_VALUE

            new String2Float16Case("65520.0", InfinityF), // Float16.MAX_VALUE + 0.5*ulp

            new String2Float16Case("65520.01", InfinityF), // Float16.MAX_VALUE + > 0.5*ulp
            new String2Float16Case("65520.001", InfinityF), // Float16.MAX_VALUE + > 0.5*ulp
            new String2Float16Case("65520.0001", InfinityF), // Float16.MAX_VALUE + > 0.5*ulp
            new String2Float16Case("65520.00000000001", InfinityF), // Float16.MAX_VALUE + > 0.5*ulp

            new String2Float16Case("65519.99999999999", 65504.0f), // Float16.MAX_VALUE +  < 0.5*ulp
            new String2Float16Case("0x1.ffdffffffffffp15", 65504.0f),
            new String2Float16Case("0x1.ffdfffffffffp15", 65504.0f),


            new String2Float16Case("65519.999999999999", 65504.0f),
            new String2Float16Case("65519.9999999999999", 65504.0f),
            new String2Float16Case("65519.99999999999999", 65504.0f),
            new String2Float16Case("65519.999999999999999", 65504.0f),

            // Float16.MAX_VALUE +  < 0.5*ulp
            new String2Float16Case("65519.9999999999999999999999999999999999999", 65504.0f),

            // Near MAX_VALUE - 0.5 ulp
            new String2Float16Case("65488.0", 65472.0f),
            new String2Float16Case("65487.9999", 65472.0f),
            new String2Float16Case("65487.99999999", 65472.0f),
            new String2Float16Case("65487.9999999999999999", 65472.0f),

            new String2Float16Case("65488.000001", MAX_VAL_FP16),

            new String2Float16Case("65536.0", InfinityF), // Float16.MAX_VALUE + ulp

            // Hex values
            new String2Float16Case("0x1p2",   0x1.0p2f),
            new String2Float16Case("0x1p2f",  0x1.0p2f),
            new String2Float16Case("0x1p2d",  0x1.0p2f),
            new String2Float16Case("0x1.0p1", 0x1.0p1f),

            new String2Float16Case("-0x1p2",  -0x1.0p2f),
            new String2Float16Case("0x3.45p12", 0x3.45p12f),

            new String2Float16Case("0x3.4500000001p12", 0x3.45p12f),

            // Near half-way double + float cases in hex
            new String2Float16Case("0x1.ffdfffffffffffffffffffffffffffffffffffp15", 65504.0f),

        };

        for(String2Float16Case testCase : testCases) {
            String input = testCase.input();
            float expected = testCase.expected();
            Float16 result = Float16.valueOf(input);
            checkFloat16(result, expected, "Float16.valueOfExact(String) " + input);
        }

        List<String> negativeCases = List.of("0x1",
                                       "-0x1",
                                        "0x12",
                                       "-0x12");

        for(String negativeCase : negativeCases) {
            try {
                Float16 f16 = Float16.valueOf(negativeCase);
                throwRE("Did not get expected exception for input " + negativeCase);
            } catch (NumberFormatException nfe) {
                ; // Expected
            }
        }

        return;
    }

    private static record String2Float16Case(String input, float expected) {
    }

    private static void checkBaseConversionRoundTrip() {
        checkFloat16(Float16.NaN,
                     Float16.valueOf("NaN").floatValue(),
                     "base conversion of NaN");

        // For each non-NaN value, make sure
        // value -> string -> value
        // sequence of conversions gives the expected result.

        for(int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Float16 f16 = Float16.shortBitsToFloat16((short)i);
            if (Float16.isNaN(f16))
                continue;

            checkFloat16(f16,
                         Float16.valueOf(Float16.toString(f16)).floatValue(),
                         "base conversion");
        }
        return;
    }

    private static class FusedMultiplyAddTests {
        public static void main(String... args) {
            testZeroNanInfCombos();
            testNonFinite();
            testZeroes();
            testSimple();
            testRounding();
        }

        private static void testZeroNanInfCombos() {
            float [] testInputs = {
                Float.NaN,
                -InfinityF,
                +InfinityF,
                -0.0f,
                +0.0f,
            };

            for (float i : testInputs) {
                for (float j : testInputs) {
                    for (float k : testInputs) {
                        testFusedMacCase(i, j, k, Math.fma(i, j, k));
                    }
                }
            }
        }

        private static void testNonFinite() {
            float [][] testCases = {
                {1.0f,       InfinityF,  2.0f,
                 InfinityF},

                {1.0f,       2.0f,       InfinityF,
                 InfinityF},

                {InfinityF,  1.0f,       InfinityF,
                 InfinityF},

                {0x1.ffcp14f, 2.0f,     -InfinityF,
                 -InfinityF},

                {InfinityF,  1.0f,      -InfinityF,
                 NaNf},

                {-InfinityF, 1.0f,       InfinityF,
                 NaNf},

                {1.0f,       NaNf,       2.0f,
                 NaNf},

                {1.0f,       2.0f,       NaNf,
                 NaNf},

                {InfinityF,  2.0f,       NaNf,
                 NaNf},

                {NaNf,       2.0f,       InfinityF,
                 NaNf},
            };

            for (float[] testCase: testCases) {
                testFusedMacCase(testCase[0], testCase[1], testCase[2], testCase[3]);
            }
        }

        private static void testZeroes() {
            float [][] testCases = {
                {+0.0f, +0.0f, +0.0f,
                 +0.0f},

                {-0.0f, +0.0f, +0.0f,
                 +0.0f},

                {+0.0f, +0.0f, -0.0f,
                 +0.0f},

                {+0.0f, +0.0f, -0.0f,
                 +0.0f},

                {-0.0f, +0.0f, -0.0f,
                 -0.0f},

                {-0.0f, -0.0f, -0.0f,
                 +0.0f},

                {-1.0f, +0.0f, -0.0f,
                 -0.0f},

                {-1.0f, +0.0f, +0.0f,
                 +0.0f},

                {-2.0f, +0.0f, -0.0f,
                 -0.0f},
            };

            for (float[] testCase: testCases) {
                testFusedMacCase(testCase[0], testCase[1], testCase[2], testCase[3]);
            }
        }

        private static void testSimple() {
            final float ulpOneFp16 = ulp(valueOfExact(1.0f)).floatValue();

            float [][] testCases = {
                {1.0f, 2.0f, 3.0f,
                 5.0f},

                {1.0f, 2.0f, -2.0f,
                 0.0f},

                {5.0f, 5.0f, -25.0f,
                 0.0f},

                {0.5f*MAX_VAL_FP16, 2.0f, -0.5f*MAX_VAL_FP16,
                 0.5f*MAX_VAL_FP16},

                {MAX_VAL_FP16, 2.0f, -MAX_VAL_FP16,
                 MAX_VAL_FP16},

                {MAX_VAL_FP16, 2.0f, 1.0f,
                 InfinityF},

                {(1.0f + ulpOneFp16),
                 (1.0f + ulpOneFp16),
                 -1.0f - 2.0f*ulpOneFp16,
                 ulpOneFp16 * ulpOneFp16},

            };

            for (float[] testCase: testCases) {
                testFusedMacCase(testCase[0], testCase[1], testCase[2], testCase[3]);
            }
        }

        private static void testRounding() {
            final float ulpOneFp16 = ulp(valueOfExact(1.0f)).floatValue();

            float [][] testCases = {
                // The product is equal to
                // (MAX_VALUE + 1/2 * ulp(MAX_VALUE) + MAX_VALUE = (0x1.ffcp15 + 0x0.002p15)+ 0x1.ffcp15
                // so overflows.
                {0x1.3p1f, 0x1.afp15f, -MAX_VAL_FP16,
                 InfinityF},

                // Product exactly equals 0x1.ffep15, the overflow
                // threshold; subtracting a non-zero finite value will
                // result in MAX_VALUE, adding zero or a positive
                // value will overflow.
                {0x1.2p10f, 0x1.c7p5f, -0x1.0p-14f,
                 MAX_VAL_FP16},

                {0x1.2p10f, 0x1.c7p5f, -0.0f,
                 InfinityF},

                {0x1.2p10f, 0x1.c7p5f, +0.0f,
                 InfinityF},

                {0x1.2p10f, 0x1.c7p5f, +0x1.0p-14f,
                 InfinityF},

                {0x1.2p10f, 0x1.c7p5f, InfinityF,
                 InfinityF},

                // PRECISION bits in the subnormal intermediate product
                {0x1.ffcp-14f, 0x1.0p-24f, 0x1.0p13f, // Can be held exactly
                 0x1.0p13f},

                {0x1.ffcp-14f, 0x1.0p-24f, 0x1.0p14f, // *Cannot* be held exactly
                 0x1.0p14f},

                // Arguments where using float fma or uniform float
                // arithmetic gives the wrong result
                {0x1.08p7f, 0x1.04p7f, 0x1.0p-24f,
                 0x1.0c4p14f},

                // Check values where the exact result cannot be
                // exactly stored in a double.
                {0x1.0p-24f, 0x1.0p-24f, 0x1.0p10f,
                 0x1.0p10f},

                {0x1.0p-24f, 0x1.0p-24f, 0x1.0p14f,
                 0x1.0p14f},

                // Check subnormal results, underflow to zero
                {0x1.0p-24f, -0.5f, 0x1.0p-24f,
                 0.0f},

                // Check subnormal results, underflow to zero
                {0x1.0p-24f, -0.5f, 0.0f,
                 -0.0f},
            };

            for (float[] testCase: testCases) {
                testFusedMacCase(testCase[0], testCase[1], testCase[2], testCase[3]);
            }
        }

        private static void testFusedMacCase(float input1, float input2, float input3, float expected) {
            Float16 a = valueOfExact(input1);
            Float16 b = valueOfExact(input2);
            Float16 c = valueOfExact(input3);
            Float16 d = valueOfExact(expected);

            test("Float16.fma(float)", a, b, c, Float16.fma(a, b, c), d);

            // Permute first two inputs
            test("Float16.fma(float)", b, a, c, Float16.fma(b, a, c), d);
            return;
        }
    }

    private static void test(String testName,
                           Float16 input1, Float16 input2, Float16 input3,
                           Float16 result, Float16 expected) {
        if (Float16.compare(expected, result ) != 0) {
            System.err.println("Failure for "  + testName + ":\n" +
                               "\tFor inputs " + input1   + "\t(" + toHexString(input1) + ") and "
                                               + input2   + "\t(" + toHexString(input2) + ") and"
                                               + input3   + "\t(" + toHexString(input3) + ")\n"  +
                               "\texpected  "  + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       "  + result   + "\t(" + toHexString(result) + ").");
            throw new RuntimeException();
        }
    }

    /**
     * {@return a Float16 value converted from the {@code float}
     * argument throwing an {@code ArithmeticException} if the
     * conversion is inexact}.
     *
     * @param f the {@code float} value to convert exactly
     * @throws ArithmeticException
     */
    private static Float16 valueOfExact(float f) {
        Float16 f16 = valueOf(f);
        if (Float.compare(f16.floatValue(), f) != 0) {
            throw new ArithmeticException("Inexact conversion to Float16 of float value " + f);
        }
        return f16;
    }
}
