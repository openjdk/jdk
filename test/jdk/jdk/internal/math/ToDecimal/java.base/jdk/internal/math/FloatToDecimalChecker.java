/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.Float.floatToRawIntBits;
import static java.lang.Float.intBitsToFloat;
import static java.lang.Integer.numberOfTrailingZeros;

public final class FloatToDecimalChecker extends ToDecimalChecker {

    private static final int P = numberOfTrailingZeros(floatToRawIntBits(3)) + 2;
    private static final int W = w(P);

    private static final int Q_MIN = q_min(P);
    private static final int Q_MAX = q_max(P);
    private static final long C_MIN = c_min(P);
    private static final long C_MAX = c_max(P);

    private static final int E_MIN = e_min(P);
    private static final int E_MAX = e_max(P);
    private static final int E_THR_Z = e_thr_z(P);
    private static final int E_THR_I = e_thr_i(P);
    private static final int K_MIN = k_min(P);
    private static final int K_MAX = k_max(P);

    private static final int C_TINY = c_tiny(P);
    private static final int H = h(P);

    private static final BigDecimal MIN_VALUE = min_value(P);
    private static final BigDecimal MIN_NORMAL = min_normal(P);
    private static final BigDecimal MAX_VALUE = max_value(P);

    private static final int Z = 1_024;

    private final float v;

    private FloatToDecimalChecker(float v) {
        super(FloatToDecimal.toString(v));
        this.v = v;
    }

    @Override
    int eMin() {
        return E_MIN;
    }

    @Override
    int eMax() {
        return E_MAX;
    }

    @Override
    int h() {
        return H;
    }

    @Override
    int maxStringLength() {
        return H + 6;
    }

    @Override
    BigDecimal toBigDecimal() {
        return new BigDecimal(v);
    }

    @Override
    boolean recovers(BigDecimal bd) {
        return bd.floatValue() == v;
    }

    @Override
    boolean recovers(String s) {
        return Float.parseFloat(s) == v;
    }

    @Override
    String hexString() {
        return Float.toHexString(v) + "F";
    }

    @Override
    boolean isNegativeInfinity() {
        return v == Float.NEGATIVE_INFINITY;
    }

    @Override
    boolean isPositiveInfinity() {
        return v == Float.POSITIVE_INFINITY;
    }

    @Override
    boolean isMinusZero() {
        return floatToRawIntBits(v) == 0x8000_0000;
    }

    @Override
    boolean isPlusZero() {
        return floatToRawIntBits(v) == 0x0000_0000;
    }

    @Override
    boolean isNaN() {
        return Float.isNaN(v);
    }

    private static void testDec(float v) {
        new FloatToDecimalChecker(v).check();
    }

    /*
     * Test around v, up to z values below and above v.
     * Don't care when v is at the extremes,
     * as any value returned by intBitsToFloat() is valid.
     */
    private static void testAround(float v, int z) {
        int bits = floatToRawIntBits(v);
        for (int i = -z; i <= z; ++i) {
            testDec(intBitsToFloat(bits + i));
        }
    }

    /*
     * MIN_NORMAL is incorrectly rendered by older JDKs.
     */
    private static void testExtremeValues() {
        testDec(Float.NEGATIVE_INFINITY);
        testAround(-Float.MAX_VALUE, Z);
        testAround(-Float.MIN_NORMAL, Z);
        testAround(-Float.MIN_VALUE, Z);
        testDec(-0.0f);
        testDec(0.0f);
        testAround(Float.MIN_VALUE, Z);
        testAround(Float.MIN_NORMAL, Z);
        testAround(Float.MAX_VALUE, Z);
        testDec(Float.POSITIVE_INFINITY);
        testDec(Float.NaN);

        /*
         * Quiet NaNs have the most significant bit of the mantissa as 1,
         * while signaling NaNs have it as 0.
         * Exercise 4 combinations of quiet/signaling NaNs and
         * "positive/negative" NaNs.
         */
        testDec(intBitsToFloat(0x7FC0_0001));
        testDec(intBitsToFloat(0x7F80_0001));
        testDec(intBitsToFloat(0xFFC0_0001));
        testDec(intBitsToFloat(0xFF80_0001));

        /*
         * All values treated specially by Schubfach
         */
        for (int c = 1; c < C_TINY; ++c) {
            testDec(c * Float.MIN_VALUE);
        }
    }

    /*
     * Some values close to powers of 10 are incorrectly rendered by older JDKs.
     * The rendering is either too long or it is not the closest decimal.
     */
    private static void testPowersOf10() {
        for (int e = E_MIN; e <= E_MAX; ++e) {
            testAround(Float.parseFloat("1e" + e), Z);
        }
    }

    /*
     * Many powers of 2 are incorrectly rendered by older JDKs.
     * The rendering is either too long or it is not the closest decimal.
     */
    private static void testPowersOf2() {
        for (float v = Float.MIN_VALUE; v <= Float.MAX_VALUE; v *= 2) {
            testAround(v, Z);
        }
    }

    /*
     * There are tons of floats that are rendered incorrectly by older JDKs.
     * While the renderings correctly round back to the original value,
     * they are longer than needed or are not the closest decimal to the float.
     * Here are just a very few examples.
     */
    private static final String[] Anomalies = {
            /* Older JDKs render these longer than needed */
            "1.1754944E-38", "2.2E-44",
            "1.0E16", "2.0E16", "3.0E16", "5.0E16", "3.0E17",
            "3.2E18", "3.7E18", "3.7E16", "3.72E17", "2.432902E18",

            /* Older JDKs do not render this as the closest */
            "9.9E-44",
    };

    private static void testSomeAnomalies() {
        for (String dec : Anomalies) {
            testDec(Float.parseFloat(dec));
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
            testDec(StrictMath.scalb(PaxsonSignificands[i], PaxsonExponents[i]));
        }
    }

    /*
     * Tests all positive integers below 2^23.
     * These are all exact floats and exercise the fast path.
     */
    private static void testInts() {
        for (int i = 1; i < 1 << P - 1; ++i) {
            testDec(i);
        }
    }

    /*
     * 0.1, 0.2, ..., 999.9 and around
     */
    private static void testDeci() {
        for (int i = 1; i < 10_000; ++i) {
            testAround(i / 1e1f, 10);
        }
    }

    /*
     * 0.01, 0.02, ..., 99.99 and around
     */
    private static void testCenti() {
        for (int i = 1; i < 10_000; ++i) {
            testAround(i / 1e2f, 10);
        }
    }

    /*
     * 0.001, 0.002, ..., 9.999 and around
     */
    private static void testMilli() {
        for (int i = 1; i < 10_000; ++i) {
            testAround(i / 1e3f, 10);
        }
    }

    /*
     * Random floats over the whole range.
     */
    private static void testRandom(int randomCount, Random r) {
        for (int i = 0; i < randomCount; ++i) {
            testDec(intBitsToFloat(r.nextInt()));
        }
    }

    /*
     * All, really all, 2^32 possible floats. Takes between 90 and 120 minutes.
     */
    public static void testAll() {
        /* Avoid wrapping around Integer.MAX_VALUE */
        int bits = Integer.MIN_VALUE;
        for (; bits < Integer.MAX_VALUE; ++bits) {
            testDec(intBitsToFloat(bits));
        }
        testDec(intBitsToFloat(bits));
    }

    /*
     * All positive 2^31 floats.
     */
    public static void testPositive() {
        /* Avoid wrapping around Integer.MAX_VALUE */
        int bits = 0;
        for (; bits < Integer.MAX_VALUE; ++bits) {
            testDec(intBitsToFloat(bits));
        }
        testDec(intBitsToFloat(bits));
    }

    /*
     * Values suggested by Guy Steele
     */
    private static void testRandomShortDecimals(Random r) {
        int e = r.nextInt(E_MAX - E_MIN + 1) + E_MIN;
        for (int pow10 = 1; pow10 < 10_000; pow10 *= 10) {
            /* randomly generate an int in [pow10, 10 pow10) */
            testAround(Float.parseFloat((r.nextInt(9 * pow10) + pow10) + "e" + e), Z);
        }
    }

    private static void testConstants() {
        addOnFail(P == FloatToDecimal.P, "P");
        addOnFail(W == FloatToDecimal.W, "W");

        addOnFail(Q_MIN == FloatToDecimal.Q_MIN, "Q_MIN");
        addOnFail(Q_MAX == FloatToDecimal.Q_MAX, "Q_MAX");
        addOnFail(C_MIN == FloatToDecimal.C_MIN, "C_MIN");
        addOnFail(C_MAX == FloatToDecimal.C_MAX, "C_MAX");
        addOnFail((int) (float) C_MIN == C_MIN, "C_MIN");
        addOnFail((int) (float) C_MAX == C_MAX, "C_MAX");

        addOnFail(E_MIN == FloatToDecimal.E_MIN, "E_MIN");
        addOnFail(E_MAX == FloatToDecimal.E_MAX, "E_MAX");
        addOnFail(E_THR_Z == FloatToDecimal.E_THR_Z, "E_THR_Z");
        addOnFail(E_THR_I == FloatToDecimal.E_THR_I, "E_THR_I");
        addOnFail(K_MIN == FloatToDecimal.K_MIN, "K_MIN");
        addOnFail(K_MAX == FloatToDecimal.K_MAX, "K_MAX");

        addOnFail(C_TINY == FloatToDecimal.C_TINY, "C_TINY");
        addOnFail(H == FloatToDecimal.H, "H");

        addOnFail(MIN_VALUE.compareTo(new BigDecimal(Float.MIN_VALUE)) == 0, "MIN_VALUE");
        addOnFail(MIN_NORMAL.compareTo(new BigDecimal(Float.MIN_NORMAL)) == 0, "MIN_NORMAL");
        addOnFail(MAX_VALUE.compareTo(new BigDecimal(Float.MAX_VALUE)) == 0, "MAX_VALUE");
    }

    public static void test(int randomCount, Random r) {
        testConstants();
        testExtremeValues();
        testSomeAnomalies();
        testPowersOf2();
        testPowersOf10();
        testPaxson();
        testInts();
        testDeci();
        testCenti();
        testMilli();
        testRandomShortDecimals(r);
        testRandom(randomCount, r);
        throwOnErrors("FloatToDecimalChecker");
    }

}
