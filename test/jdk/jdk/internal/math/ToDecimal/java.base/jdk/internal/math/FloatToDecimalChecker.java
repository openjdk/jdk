/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.math;

import java.math.BigDecimal;
import java.util.Random;

import static java.lang.Float.*;
import static java.lang.Integer.numberOfTrailingZeros;
import static java.lang.StrictMath.scalb;
import static jdk.internal.math.MathUtils.flog10pow2;

public class FloatToDecimalChecker extends ToDecimalChecker {

    private static final int P =
            numberOfTrailingZeros(floatToRawIntBits(3)) + 2;
    private static final int W = (SIZE - 1) - (P - 1);
    private static final int Q_MIN = (-1 << (W - 1)) - P + 3;
    private static final int Q_MAX = (1 << (W - 1)) - P;
    private static final int C_MIN = 1 << (P - 1);
    private static final int C_MAX = (1 << P) - 1;

    private static final int K_MIN = flog10pow2(Q_MIN);
    private static final int K_MAX = flog10pow2(Q_MAX);
    private static final int H = flog10pow2(P) + 2;

    private static final float MIN_VALUE = scalb(1.0f, Q_MIN);
    private static final float MIN_NORMAL = scalb((float) C_MIN, Q_MIN);
    private static final float MAX_VALUE = scalb((float) C_MAX, Q_MAX);

    private static final int E_MIN = e(MIN_VALUE);
    private static final int E_MAX = e(MAX_VALUE);

    private static final long C_TINY = cTiny(Q_MIN, K_MIN);

    private float v;
    private final int originalBits;

    private FloatToDecimalChecker(float v, String s) {
        super(s);
        this.v = v;
        originalBits = floatToRawIntBits(v);
    }

    @Override
    BigDecimal toBigDecimal() {
        return new BigDecimal(v);
    }

    @Override
    boolean recovers(BigDecimal b) {
        return b.floatValue() == v;
    }

    @Override
    String hexBits() {
        return String.format("0x%01X__%02X__%02X_%04X",
                (originalBits >>> 31) & 0x1,
                (originalBits >>> 23) & 0xFF,
                (originalBits >>> 16) & 0x7F,
                originalBits & 0xFFFF);
    }

    @Override
    boolean recovers(String s) {
        return parseFloat(s) == v;
    }

    @Override
    int minExp() {
        return E_MIN;
    }

    @Override
    int maxExp() {
        return E_MAX;
    }

    @Override
    int maxLen10() {
        return H;
    }

    @Override
    boolean isZero() {
        return v == 0;
    }

    @Override
    boolean isInfinity() {
        return v == POSITIVE_INFINITY;
    }

    @Override
    void negate() {
        v = -v;
    }

    @Override
    boolean isNegative() {
        return originalBits < 0;
    }

    @Override
    boolean isNaN() {
        return Float.isNaN(v);
    }

    private static void toDec(float v) {
        String s = FloatToDecimal.toString(v);
        new FloatToDecimalChecker(v, s).assertTrue();
    }

    /*
     * MIN_NORMAL is incorrectly rendered by the JDK.
     */
    private static void testExtremeValues() {
        toDec(NEGATIVE_INFINITY);
        toDec(-MAX_VALUE);
        toDec(-MIN_NORMAL);
        toDec(-MIN_VALUE);
        toDec(-0.0f);
        toDec(0.0f);
        toDec(MIN_VALUE);
        toDec(MIN_NORMAL);
        toDec(MAX_VALUE);
        toDec(POSITIVE_INFINITY);
        toDec(NaN);

        /*
         * Quiet NaNs have the most significant bit of the mantissa as 1,
         * while signaling NaNs have it as 0.
         * Exercise 4 combinations of quiet/signaling NaNs and
         * "positive/negative" NaNs.
         */
        toDec(intBitsToFloat(0x7FC0_0001));
        toDec(intBitsToFloat(0x7F80_0001));
        toDec(intBitsToFloat(0xFFC0_0001));
        toDec(intBitsToFloat(0xFF80_0001));

        /*
         * All values treated specially by Schubfach
         */
        for (int c = 1; c < C_TINY; ++c) {
            toDec(c * MIN_VALUE);
        }
    }

    /*
     * Some "powers of 10" are incorrectly rendered by older JDK.
     * The rendering is either too long or it is not the closest decimal.
     */
    private static void testPowersOf10() {
        for (int e = E_MIN; e <= E_MAX; ++e) {
            toDec(parseFloat("1e" + e));
        }
    }

    /*
     * Many powers of 2 are incorrectly rendered by older JDK.
     * The rendering is either too long or it is not the closest decimal.
     */
    private static void testPowersOf2() {
        for (float v = MIN_VALUE; v <= MAX_VALUE; v *= 2) {
            toDec(v);
        }
    }

    /*
     * There are tons of doubles that are rendered incorrectly by older JDK.
     * While the renderings correctly round back to the original value,
     * they are longer than needed or are not the closest decimal to the double.
     * Here are just a very few examples.
     */
    private static final String[] Anomalies = {
            /* JDK renders these longer than needed */
            "1.1754944E-38", "2.2E-44",
            "1.0E16", "2.0E16", "3.0E16", "5.0E16", "3.0E17",
            "3.2E18", "3.7E18", "3.7E16", "3.72E17",

            /* JDK does not render this as the closest */
            "9.9E-44",
    };

    private static void testSomeAnomalies() {
        for (String dec : Anomalies) {
            toDec(parseFloat(dec));
        }
    }

    /*
     * Values are from
     * Paxson V, "A Program for Testing IEEE Decimal-Binary Conversion"
     * tables 16 and 17
     */
    private static final float[] PaxsonSignificands = {
            12_676_506,
            15_445_013,
            13_734_123,
            12_428_269,
            12_676_506,
            15_334_037,
            11_518_287,
            12_584_953,
            15_961_084,
            14_915_817,
            10_845_484,
            16_431_059,

            16_093_626,
             9_983_778,
            12_745_034,
            12_706_553,
            11_005_028,
            15_059_547,
            16_015_691,
             8_667_859,
            14_855_922,
            14_855_922,
            10_144_164,
            13_248_074,
    };

    private static final int[] PaxsonExponents = {
            -102,
            -103,
              86,
            -138,
            -130,
            -146,
             -41,
            -145,
            -125,
            -146,
            -102,
             -61,

              69,
              25,
             104,
              72,
              45,
              71,
             -99,
              56,
             -82,
             -83,
            -110,
              95,
    };

    private static void testPaxson() {
        for (int i = 0; i < PaxsonSignificands.length; ++i) {
            toDec(scalb(PaxsonSignificands[i], PaxsonExponents[i]));
        }
    }

    /*
     * Tests all positive integers below 2^23.
     * These are all exact floats and exercise the fast path.
     */
    private static void testInts() {
        for (int i = 1; i < 1 << P - 1; ++i) {
            toDec(i);
        }
    }

    /*
     * Random floats over the whole range.
     */
    private static void testRandom(int randomCount, Random r) {
        for (int i = 0; i < randomCount; ++i) {
            toDec(intBitsToFloat(r.nextInt()));
        }
    }

    /*
     * All, really all, 2^32 possible floats. Takes between 90 and 120 minutes.
     */
    public static void testAll() {
        /* Avoid wrapping around Integer.MAX_VALUE */
        int bits = Integer.MIN_VALUE;
        for (; bits < Integer.MAX_VALUE; ++bits) {
            toDec(intBitsToFloat(bits));
        }
        toDec(intBitsToFloat(bits));
    }

    /*
     * All positive 2^31 floats.
     */
    public static void testPositive() {
        /* Avoid wrapping around Integer.MAX_VALUE */
        int bits = 0;
        for (; bits < Integer.MAX_VALUE; ++bits) {
            toDec(intBitsToFloat(bits));
        }
        toDec(intBitsToFloat(bits));
    }

    private static void testConstants() {
        assertTrue(P == FloatToDecimal.P, "P");
        assertTrue((long) (float) C_MIN == C_MIN, "C_MIN");
        assertTrue((long) (float) C_MAX == C_MAX, "C_MAX");
        assertTrue(MIN_VALUE == Float.MIN_VALUE, "MIN_VALUE");
        assertTrue(MIN_NORMAL == Float.MIN_NORMAL, "MIN_NORMAL");
        assertTrue(MAX_VALUE == Float.MAX_VALUE, "MAX_VALUE");

        assertTrue(Q_MIN == FloatToDecimal.Q_MIN, "Q_MIN");
        assertTrue(Q_MAX == FloatToDecimal.Q_MAX, "Q_MAX");

        assertTrue(K_MIN == FloatToDecimal.K_MIN, "K_MIN");
        assertTrue(K_MAX == FloatToDecimal.K_MAX, "K_MAX");
        assertTrue(H == FloatToDecimal.H, "H");

        assertTrue(E_MIN == FloatToDecimal.E_MIN, "E_MIN");
        assertTrue(E_MAX == FloatToDecimal.E_MAX, "E_MAX");
        assertTrue(C_TINY == FloatToDecimal.C_TINY, "C_TINY");
    }

    public static void test(int randomCount, Random r) {
        testConstants();
        testExtremeValues();
        testSomeAnomalies();
        testPowersOf2();
        testPowersOf10();
        testPaxson();
        testInts();
        testRandom(randomCount, r);
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("all")) {
            testAll();
            return;
        }
        if (args.length > 0 && args[0].equals("positive")) {
            testPositive();
            return;
        }
        test(1_000_000, new Random());
    }

}
