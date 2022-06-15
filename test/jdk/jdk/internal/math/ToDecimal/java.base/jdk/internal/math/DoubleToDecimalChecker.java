/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.Double.*;
import static java.lang.Long.numberOfTrailingZeros;
import static java.lang.StrictMath.scalb;
import static jdk.internal.math.MathUtils.flog10pow2;

public class DoubleToDecimalChecker extends ToDecimalChecker {

    private static final int P =
            numberOfTrailingZeros(doubleToRawLongBits(3)) + 2;
    private static final int W = (SIZE - 1) - (P - 1);
    private static final int Q_MIN = (-1 << (W - 1)) - P + 3;
    private static final int Q_MAX = (1 << (W - 1)) - P;
    private static final long C_MIN = 1L << (P - 1);
    private static final long C_MAX = (1L << P) - 1;

    private static final int K_MIN = flog10pow2(Q_MIN);
    private static final int K_MAX = flog10pow2(Q_MAX);
    private static final int H = flog10pow2(P) + 2;

    private static final double MIN_VALUE = scalb(1.0, Q_MIN);
    private static final double MIN_NORMAL = scalb((double) C_MIN, Q_MIN);
    private static final double MAX_VALUE = scalb((double) C_MAX, Q_MAX);

    private static final int E_MIN = e(MIN_VALUE);
    private static final int E_MAX = e(MAX_VALUE);

    private static final long C_TINY = cTiny(Q_MIN, K_MIN);

    private static final int Z = 1_024;

    private final double v;

    private DoubleToDecimalChecker(double v) {
        super(DoubleToDecimal.toString(v));
//        super(Double.toString(v));
        this.v = v;
    }

    @Override
    int h() {
        return H;
    }

    @Override
    int maxStringLength() {
        return H + 7;
    }

    @Override
    BigDecimal toBigDecimal() {
        return new BigDecimal(v);
    }

    @Override
    boolean recovers(BigDecimal bd) {
        return bd.doubleValue() == v;
    }

    @Override
    boolean recovers(String s) {
        return parseDouble(s) == v;
    }

    @Override
    String hexString() {
        return toHexString(v) + "D";
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
    boolean isNegativeInfinity() {
        return v == NEGATIVE_INFINITY;
    }

    @Override
    boolean isPositiveInfinity() {
        return v == POSITIVE_INFINITY;
    }

    @Override
    boolean isMinusZero() {
        return doubleToRawLongBits(v) == 0x8000_0000_0000_0000L;
    }

    @Override
    boolean isPlusZero() {
        return doubleToRawLongBits(v) == 0x0000_0000_0000_0000L;
    }

    @Override
    boolean isNaN() {
        return Double.isNaN(v);
    }

    /*
     * Convert v to String and check whether it meets the specification.
     */
    private static void testDec(double v) {
        new DoubleToDecimalChecker(v).check();
    }

    /*
     * Test around v, up to z values below and above v.
     * Don't care when v is at the extremes,
     * as any value returned by longBitsToDouble() is valid.
     */
    private static void testAround(double v, int z) {
        long bits = doubleToRawLongBits(v);
        for (int i = -z; i <= z; ++i) {
            testDec(longBitsToDouble(bits + i));
        }
    }

    private static void testExtremeValues() {
        testDec(NEGATIVE_INFINITY);
        testAround(-MAX_VALUE, Z);
        testAround(-MIN_NORMAL, Z);
        testAround(-MIN_VALUE, Z);
        testDec(-0.0);
        testDec(0.0);
        testAround(MIN_VALUE, Z);
        testAround(MIN_NORMAL, Z);
        testAround(MAX_VALUE, Z);
        testDec(POSITIVE_INFINITY);
        testDec(NaN);

        /*
         * Quiet NaNs have the most significant bit of the mantissa as 1,
         * while signaling NaNs have it as 0.
         * Exercise 4 combinations of quiet/signaling NaNs and
         * "positive/negative" NaNs
         */
        testDec(longBitsToDouble(0x7FF8_0000_0000_0001L));
        testDec(longBitsToDouble(0x7FF0_0000_0000_0001L));
        testDec(longBitsToDouble(0xFFF8_0000_0000_0001L));
        testDec(longBitsToDouble(0xFFF0_0000_0000_0001L));

        /*
         * All values treated specially by Schubfach
         */
        for (int c = 1; c < C_TINY; ++c) {
            testDec(c * MIN_VALUE);
        }
    }

    /*
     * Some values close to powers of 10 are incorrectly rendered by older JDKs.
     * The rendering is either too long or it is not the closest decimal.
     */
    private static void testPowersOf10() {
        for (int e = E_MIN; e <= E_MAX; ++e) {
            testAround(parseDouble("1e" + e), Z);
        }
    }

    /*
     * Many values close to powers of 2 are incorrectly rendered by older JDKs.
     * The rendering is either too long or it is not the closest decimal.
     */
    private static void testPowersOf2() {
        for (double v = MIN_VALUE; v <= MAX_VALUE; v *= 2) {
            testAround(v, Z);
        }
    }

    /*
     * There are tons of doubles that are rendered incorrectly by older JDKs.
     * While the renderings correctly round back to the original value,
     * they are longer than needed or are not the closest decimal to the double.
     * Here are just a very few examples.
     */
    private static final String[] Anomalies = {
            /* Older JDKs render these with 18 digits! */
            "2.82879384806159E17", "1.387364135037754E18",
            "1.45800632428665E17",

            /* Older JDKs render these longer than needed */
            "1.6E-322", "6.3E-322",
            "7.3879E20", "2.0E23", "7.0E22", "9.2E22",
            "9.5E21", "3.1E22", "5.63E21", "8.41E21",

            /* Older JDKs do not render these as the closest */
            "9.9E-324", "9.9E-323",
            "1.9400994884341945E25", "3.6131332396758635E25",
            "2.5138990223946153E25",
    };

    private static void testSomeAnomalies() {
        for (String dec : Anomalies) {
            testDec(parseDouble(dec));
        }
    }

    /*
     * Values are from
     * Paxson V, "A Program for Testing IEEE Decimal-Binary Conversion"
     * tables 3 and 4
     */
    private static final double[] PaxsonSignificands = {
            8_511_030_020_275_656L,
            5_201_988_407_066_741L,
            6_406_892_948_269_899L,
            8_431_154_198_732_492L,
            6_475_049_196_144_587L,
            8_274_307_542_972_842L,
            5_381_065_484_265_332L,
            6_761_728_585_499_734L,
            7_976_538_478_610_756L,
            5_982_403_858_958_067L,
            5_536_995_190_630_837L,
            7_225_450_889_282_194L,
            7_225_450_889_282_194L,
            8_703_372_741_147_379L,
            8_944_262_675_275_217L,
            7_459_803_696_087_692L,
            6_080_469_016_670_379L,
            8_385_515_147_034_757L,
            7_514_216_811_389_786L,
            8_397_297_803_260_511L,
            6_733_459_239_310_543L,
            8_091_450_587_292_794L,

            6_567_258_882_077_402L,
            6_712_731_423_444_934L,
            6_712_731_423_444_934L,
            5_298_405_411_573_037L,
            5_137_311_167_659_507L,
            6_722_280_709_661_868L,
            5_344_436_398_034_927L,
            8_369_123_604_277_281L,
            8_995_822_108_487_663L,
            8_942_832_835_564_782L,
            8_942_832_835_564_782L,
            8_942_832_835_564_782L,
            6_965_949_469_487_146L,
            6_965_949_469_487_146L,
            6_965_949_469_487_146L,
            7_487_252_720_986_826L,
            5_592_117_679_628_511L,
            8_887_055_249_355_788L,
            6_994_187_472_632_449L,
            8_797_576_579_012_143L,
            7_363_326_733_505_337L,
            8_549_497_411_294_502L,
    };

    private static final int[] PaxsonExponents = {
            -342,
            -824,
            237,
            72,
            99,
            726,
            -456,
            -57,
            376,
            377,
            93,
            710,
            709,
            117,
            -1,
            -707,
            -381,
            721,
            -828,
            -345,
            202,
            -473,

            952,
            535,
            534,
            -957,
            -144,
            363,
            -169,
            -853,
            -780,
            -383,
            -384,
            -385,
            -249,
            -250,
            -251,
            548,
            164,
            665,
            690,
            588,
            272,
            -448,
    };

    private static void testPaxson() {
        for (int i = 0; i < PaxsonSignificands.length; ++i) {
            testDec(scalb(PaxsonSignificands[i], PaxsonExponents[i]));
        }
    }

    /*
     * Tests all integers of the form yx_xxx_000_000_000_000_000, y != 0.
     * These are all exact doubles.
     */
    private static void testLongs() {
        for (int i = 10_000; i < 100_000; ++i) {
            testDec(i * 1e15);
        }
    }

    /*
     * Tests all integers up to 1_000_000.
     * These are all exact doubles and exercise a fast path.
     */
    private static void testInts() {
        for (int i = 0; i <= 1_000_000; ++i) {
            testDec(i);
        }
    }

    /*
     * 0.1, 0.2, ..., 999.9 and around
     */
    private static void testDeci() {
        for (int i = 1; i < 10_000; ++i) {
            testAround(i / 1e1, 10);
        }
    }

    /*
     * 0.01, 0.02, ..., 99.99 and around
     */
    private static void testCenti() {
        for (int i = 1; i < 10_000; ++i) {
            testAround(i / 1e2, 10);
        }
    }

    /*
     * 0.001, 0.002, ..., 9.999 and around
     */
    private static void testMilli() {
        for (int i = 1; i < 10_000; ++i) {
            testAround(i / 1e3, 10);
        }
    }

    /*
     * Random doubles over the whole range
     */
    private static void testRandom(int randomCount, Random r) {
        for (int i = 0; i < randomCount; ++i) {
            testDec(longBitsToDouble(r.nextLong()));
        }
    }

    /*
     * Random doubles over the integer range [0, 2^52).
     * These are all exact doubles and exercise the fast path (except 0).
     */
    private static void testRandomUnit(int randomCount, Random r) {
        for (int i = 0; i < randomCount; ++i) {
            testDec(r.nextLong() & (1L << (P - 1)));
        }
    }

    /*
     * Random doubles over the range [0, 10^15) as "multiples" of 1e-3
     */
    private static void testRandomMilli(int randomCount, Random r) {
        for (int i = 0; i < randomCount; ++i) {
            testDec(r.nextLong() % 1_000_000_000_000_000_000L / 1e3);
        }
    }

    /*
     * Random doubles over the range [0, 10^15) as "multiples" of 1e-6
     */
    private static void testRandomMicro(int randomCount, Random r) {
        for (int i = 0; i < randomCount; ++i) {
            testDec((r.nextLong() & 0x7FFF_FFFF_FFFF_FFFFL) / 1e6);
        }
    }

    /*
     * Values suggested by Guy Steele
     */
    private static void testRandomShortDecimals(Random r) {
        int e = r.nextInt(E_MAX - E_MIN + 1) + E_MIN;
        for (int pow10 = 1; pow10 < 10_000; pow10 *= 10) {
            /* randomly generate an int in [pow10, 10 pow10) */
            testAround(parseDouble((r.nextInt(9 * pow10) + pow10) + "e" + e), Z);
        }
    }

    private static void testConstants() {
        addOnFail(P == DoubleToDecimal.P, "P");
        addOnFail((long) (double) C_MIN == C_MIN, "C_MIN");
        addOnFail((long) (double) C_MAX == C_MAX, "C_MAX");
        addOnFail(MIN_VALUE == Double.MIN_VALUE, "MIN_VALUE");
        addOnFail(MIN_NORMAL == Double.MIN_NORMAL, "MIN_NORMAL");
        addOnFail(MAX_VALUE == Double.MAX_VALUE, "MAX_VALUE");

        addOnFail(Q_MIN == DoubleToDecimal.Q_MIN, "Q_MIN");
        addOnFail(Q_MAX == DoubleToDecimal.Q_MAX, "Q_MAX");

        addOnFail(K_MIN == DoubleToDecimal.K_MIN, "K_MIN");
        addOnFail(K_MAX == DoubleToDecimal.K_MAX, "K_MAX");
        addOnFail(H == DoubleToDecimal.H, "H");

        addOnFail(E_MIN == DoubleToDecimal.E_MIN, "E_MIN");
        addOnFail(E_MAX == DoubleToDecimal.E_MAX, "E_MAX");
        addOnFail(C_TINY == DoubleToDecimal.C_TINY, "C_TINY");
    }

    public static void test(int randomCount, Random r) {
        testConstants();
        testExtremeValues();
        testSomeAnomalies();
        testPowersOf2();
        testPowersOf10();
        testPaxson();
        testInts();
        testLongs();
        testDeci();
        testCenti();
        testMilli();
        testRandom(randomCount, r);
        testRandomUnit(randomCount, r);
        testRandomMilli(randomCount, r);
        testRandomMicro(randomCount, r);
        testRandomShortDecimals(r);
        throwOnErrors("DoubleToDecimalChecker");
    }

}
