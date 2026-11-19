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
import java.math.BigInteger;
import java.math.RoundingMode;

import static java.math.BigInteger.*;
import static jdk.internal.math.DoubleToDecimal.*;
import static jdk.internal.math.DoubleToDecimal.Q_MAX;
import static jdk.internal.math.DoubleToDecimal.Q_MIN;
import static jdk.internal.math.MathUtils.*;

public final class MathUtilsChecker extends BasicChecker {

    private static final BigDecimal THREE_QUARTER = new BigDecimal("0.75");

    // decimal constants
    private static final int N = n();
    private static final int GE_MIN = ge_min();
    private static final int GE_MAX = ge_max();

    private static final int MARGIN = 10;

    private MathUtilsChecker() {
    }

    static BigDecimal pow2(int q) {
        return q >= 0
                ? new BigDecimal(ONE.shiftLeft(q))
                : BigDecimal.ONE.divide(new BigDecimal(ONE.shiftLeft(-q)));
    }

    static BigDecimal pow10(int e) {
        return BigDecimal.valueOf(1L, -e);
    }

    static BigInteger floor(BigDecimal v) {
        return v.setScale(0, RoundingMode.FLOOR).unscaledValue();
    }

    static BigInteger ceil(BigDecimal v) {
        return v.setScale(0, RoundingMode.CEILING).unscaledValue();
    }

    /* floor(log2(v)) */
    static int flog2(BigDecimal v) {
        /*
         * Let v = f 10^e.
         * Then log2(v) = log2(f) + e / log10(2).
         *
         * The initial flog2 is an estimate of l = floor(log2(v)), that is,
         * 2^l <= v < 2^(l+1).
         * Given the initial estimate flog2, search l meeting the above
         * inequalities.
         */
        int flog2 = (v.unscaledValue().bitLength() - 1)
                + (int) Math.floor(-v.scale() / Math.log10(2));
        for (; pow2(flog2).compareTo(v) <= 0; ++flog2);  // empty body
        for (; v.compareTo(pow2(flog2)) < 0; --flog2);  // empty body
        return flog2;
    }

    /* floor(log10(v)) */
    static int flog10(BigDecimal v) {
        return v.precision() - v.scale() - 1;
    }

    /* ceil(log10(v)) */
    static int clog10(BigDecimal v) {
        return flog10(v.subtract(v.ulp())) + 1;
    }

    /* floor(log10(2^q)) */
    static int flog10pow2(int q) {
        return flog10(pow2(q));
    }

    private static int flog10threeQuartersPow2(int q) {
        return flog10(THREE_QUARTER.multiply(pow2(q)));
    }

    private static int flog2pow10(int e) {
        return flog2(pow10(e));
    }

    private static int n() {
        return flog10pow2(Long.SIZE);
    }

    private static int ge_max() {
        return Integer.max(-K_MIN, E_THR_I - 2);
    }

    private static int ge_min() {
        return Integer.min(-K_MAX, E_THR_Z - (N - 1));
    }

    private static int r(int e) {
        return flog2pow10(e) - 125;
    }

    private static BigInteger g(int e) {
        return floor(pow10(e).multiply(pow2(-r(e)))).add(ONE);
    }

    private static String gReason(int e) {
        return "g(" + e + ") is incorrect";
    }

    private static void testG(int e) {
        long g1 = g1(e);
        long g0 = g0(e);
        // 2^62 <= g1 < 2^63, 0 < g0 < 2^63
        addOnFail((g1 >>> -2) == 0b01 && g0 > 0, gReason(e));

        BigInteger g = valueOf(g1).shiftLeft(63).or(valueOf(g0));
        // double check that 2^125 <= g < 2^126
        addOnFail(g.signum() > 0 && g.bitLength() == 126, gReason(e));

        addOnFail(g(e).compareTo(g) == 0, gReason(e));
    }

    private static void testG() {
        for (int e = GE_MIN; e <= GE_MAX; ++e) {
            testG(e);
        }
    }

    private static String flog10threeQuartersPow2Reason(int q) {
        return "flog10threeQuartersPow2(" + q + ") is incorrect";
    }

    private static void testFlog10threeQuartersPow2() {
        for (int q = Q_MIN - MARGIN; q <= Q_MAX + MARGIN; ++q) {
            addOnFail(flog10threeQuartersPow2(q) == MathUtils.flog10threeQuartersPow2(q),
                    flog10threeQuartersPow2Reason(q));
        }
    }

    private static String flog10pow2Reason(int q) {
        return "flog10pow2(" + q + ") is incorrect";
    }

    private static void testFlog10pow2() {
        for (int q = Q_MIN - MARGIN; q <= Q_MAX + MARGIN; ++q) {
            addOnFail(flog10pow2(q) == MathUtils.flog10pow2(q),
                    flog10pow2Reason(q));
        }
    }

    private static String flog2pow10Reason(int e) {
        return "flog2pow10(" + e + ") is incorrect";
    }

    private static void testFlog2pow10() {
        for (int e = -K_MAX - MARGIN; e <= -K_MIN + MARGIN; ++e) {
            addOnFail(flog2pow10(e) == MathUtils.flog2pow10(e),
                    flog2pow10Reason(e));
        }
    }

    private static void testDecimalConstants() {
        addOnFail(GE_MIN == MathUtils.GE_MIN, "GE_MIN");
        addOnFail(GE_MAX == MathUtils.GE_MAX, "GE_MAX");
    }

    private static String pow10Reason(int e) {
        return "pow10(" + e + ") is incorrect";
    }

    private static void testPow10() {
        addOnFail(N == MathUtils.N, "N");
        try {
            Math.unsignedPowExact(10L, N + 1);  // expected to throw
            addOnFail(false, "N");
        } catch (RuntimeException _) {
        }
        for (int e = 0; e <= N; ++e) {
            addOnFail(Math.unsignedPowExact(10L, e) == MathUtils.pow10(e), pow10Reason(e));
        }
    }

    public static void test() {
        testFlog10pow2();
        testFlog10threeQuartersPow2();
        testDecimalConstants();
        testFlog2pow10();
        testPow10();
        testG();
        throwOnErrors("MathUtilsChecker");
    }

}
