/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import static java.lang.Float.*;
import static java.lang.Integer.*;
import static java.lang.Math.multiplyHigh;
import static jdk.internal.math.MathUtils.*;

import sun.nio.cs.ISO_8859_1;

/**
 * This class exposes a method to render a {@code float} as a string.
 */
public final class FloatToDecimal extends ToDecimal {
    /**
     * Use LATIN1 encoding to process the in-out byte[] str
     *
     */
    public static final FloatToDecimal LATIN1 = new FloatToDecimal(true);

    /**
     * Use UTF16 encoding to process the in-out byte[] str
     *
     */
    public static final FloatToDecimal UTF16  = new FloatToDecimal(false);

    /*
     * For full details about this code see the following references:
     *
     * [1] Giulietti, "The Schubfach way to render doubles",
     *     https://drive.google.com/file/d/1gp5xv4CAa78SVgCeWfGqqI4FfYYYuNFb
     *
     * [2] IEEE Computer Society, "IEEE Standard for Floating-Point Arithmetic"
     *
     * [3] Bouvier & Zimmermann, "Division-Free Binary-to-Decimal Conversion"
     *
     * Divisions are avoided altogether for the benefit of those architectures
     * that do not provide specific machine instructions or where they are slow.
     * This is discussed in section 10 of [1].
     */

    static final int P = PRECISION;

    /* Exponent width in bits. */
    static final int W = (Float.SIZE - 1) - (P - 1);

    /* Minimum value of the exponent: -(2^(W-1)) - P + 3. */
    static final int Q_MIN = (-1 << (W - 1)) - P + 3;

    /* Maximum value of the exponent: 2^(W-1) - P. */
    static final int Q_MAX = (1 << (W - 1)) - P;

    /* Minimum value of the significand of a normal value: 2^(P-1). */
    static final int C_MIN = 1 << (P - 1);

    /* Maximum value of the significand of a normal value: 2^P - 1. */
    static final int C_MAX = (1 << P) - 1;

    /* E_MIN = max{e : 10^(e-1) <= MIN_VALUE}. */
    static final int E_MIN = -44;

    /* E_MAX = max{e : 10^(e-1) <= MAX_VALUE}. */
    static final int E_MAX = 39;

    /*
     * Let THR_Z = ulp(0.0) / 2 = MIN_VALUE / 2 = 2^(Q_MIN-1).
     * THR_Z is the zero threshold.
     * x is rounded to 0 by roundTiesToEven iff |x| <= THR_Z.
     *
     * E_THR_Z = max{e : 10^e <= THR_Z}.
     */
    static final int E_THR_Z = -46;

    /*
     * Let THR_I = MAX_VALUE + ulp(MAX_VALUE) / 2 = (2 C_MAX + 1) 2^(Q_MAX-1).
     * THR_I is the infinity threshold.
     * x is rounded to infinity by roundTiesToEven iff |x| >= THR_I.
     *
     * E_THR_I = min{e : THR_I <= 10^(e-1)}.
     */
    static final int E_THR_I = 40;

    /* K_MIN = max{k : 10^k <= 2^Q_MIN}. */
    static final int K_MIN = -45;

    /* K_MAX = max{k : 10^k <= 2^Q_MAX}. */
    static final int K_MAX = 31;

    /*
     * Threshold to detect tiny values, as in section 8.2.1 of [1].
     *      C_TINY = ceil(2^(-Q_MIN) 10^(K_MIN+1))
     */
    static final int C_TINY = 8;

    /*
     * H is as in section 8.1 of [1].
     *      H = max{e : 10^(e-2) <= 2^P}
     */
    static final int H = 9;

    /* Mask to extract the biased exponent. */
    private static final int BQ_MASK = (1 << W) - 1;

    /* Mask to extract the fraction bits. */
    private static final int T_MASK = (1 << (P - 1)) - 1;

    /* Used in rop(). */
    private static final long MASK_32 = (1L << 32) - 1;

    /*
     * Room for the longer of the forms
     *     -ddddd.dddd         H + 2 characters
     *     -0.00ddddddddd      H + 5 characters
     *     -d.ddddddddE-ee     H + 6 characters
     * where there are H digits d
     */
    public static final int MAX_CHARS = H + 6;

    private FloatToDecimal(boolean latin1) {
        super(latin1);
    }

    /**
     * Returns a string representation of the {@code float}
     * argument. All characters mentioned below are ASCII characters.
     *
     * @param   v   the {@code float} to be converted.
     * @return a string representation of the argument.
     * @see Float#toString(float)
     */
    public static String toString(float v) {
        byte[] str = new byte[MAX_CHARS];
        int pair = LATIN1.toDecimal(str, 0, v);
        int type = pair & 0xFF00;
        if (type == NON_SPECIAL) {
            int size = pair & 0xFF;
            return new String(str, 0, size, ISO_8859_1.INSTANCE);
        }
        return special(type);
    }

    /**
     * Appends the rendering of the {@code v} to {@code str}.
     *
     * <p>The outcome is the same as if {@code v} were first
     * {@link #toString(float) rendered} and the resulting string were then
     *
     * @param str the String byte array to append to
     * @param index the index into str
     * @param v the {@code float} whose rendering is into str.
     */
    public int putDecimal(byte[] str, int index, float v) {
        assert 0 <= index && index <= length(str) - MAX_CHARS : "Trusted caller missed bounds check";

        int pair = toDecimal(str, index, v);
        int type = pair & 0xFF00;
        if (type == NON_SPECIAL) {
            return index + (pair & 0xFF);
        }
        return putSpecial(str, index, type);
    }

    /*
     * Returns
     *     Combine type and size, the first byte is size, the second byte is type
     *
     *     PLUS_ZERO       iff v is 0.0
     *     MINUS_ZERO      iff v is -0.0
     *     PLUS_INF        iff v is POSITIVE_INFINITY
     *     MINUS_INF       iff v is NEGATIVE_INFINITY
     *     NAN             iff v is NaN
     */
    private int toDecimal(byte[] str, int index, float v) {
        /*
         * For full details see references [2] and [1].
         *
         * For finite v != 0, determine integers c and q such that
         *     |v| = c 2^q    and
         *     Q_MIN <= q <= Q_MAX    and
         *         either    2^(P-1) <= c < 2^P                 (normal)
         *         or        0 < c < 2^(P-1)  and  q = Q_MIN    (subnormal)
         */
        int bits = floatToRawIntBits(v);
        int t = bits & T_MASK;
        int bq = (bits >>> P - 1) & BQ_MASK;
        if (bq < BQ_MASK) {
            int start = index;
            if (bits < 0) {
                index = putChar(str, index, '-');
            }
            if (bq != 0) {
                /* normal value. Here mq = -q */
                int mq = -Q_MIN + 1 - bq;
                int c = C_MIN | t;
                /* The fast path discussed in section 8.3 of [1] */
                if (0 < mq & mq < P) {
                    int f = c >> mq;
                    if (f << mq == c) {
                        return toChars(str, index, f, 0) - start;
                    }
                }
                return toDecimal(str, index, -mq, c, 0) - start;
            }
            if (t != 0) {
                /* subnormal value */
                return (t < C_TINY
                        ? toDecimal(str, index, Q_MIN, 10 * t, -1)
                        : toDecimal(str, index, Q_MIN, t, 0)) - start;
            }
            return bits == 0 ? PLUS_ZERO : MINUS_ZERO;
        }
        if (t != 0) {
            return NAN;
        }
        return bits > 0 ? PLUS_INF : MINUS_INF;
    }

    private int toDecimal(byte[] str, int index, int q, int c, int dk) {
        /*
         * The skeleton corresponds to figure 7 of [1].
         * The efficient computations are those summarized in figure 9.
         * Also check the appendix.
         *
         * Here's a correspondence between Java names and names in [1],
         * expressed as approximate LaTeX source code and informally.
         * Other names are identical.
         * cb:     \bar{c}     "c-bar"
         * cbr:    \bar{c}_r   "c-bar-r"
         * cbl:    \bar{c}_l   "c-bar-l"
         *
         * vb:     \bar{v}     "v-bar"
         * vbr:    \bar{v}_r   "v-bar-r"
         * vbl:    \bar{v}_l   "v-bar-l"
         *
         * rop:    r_o'        "r-o-prime"
         */
        int out = c & 0x1;
        long cb = c << 2;
        long cbr = cb + 2;
        long cbl;
        int k;
        /*
         * flog10pow2(e) = floor(log_10(2^e))
         * flog10threeQuartersPow2(e) = floor(log_10(3/4 2^e))
         * flog2pow10(e) = floor(log_2(10^e))
         */
        if (c != C_MIN | q == Q_MIN) {
            /* regular spacing */
            cbl = cb - 2;
            k = flog10pow2(q);
        } else {
            /* irregular spacing */
            cbl = cb - 1;
            k = flog10threeQuartersPow2(q);
        }
        int h = q + flog2pow10(-k) + 33;

        /* g is as in the appendix */
        long g = g1(-k) + 1;

        int vb = rop(g, cb << h);
        int vbl = rop(g, cbl << h);
        int vbr = rop(g, cbr << h);

        int s = vb >> 2;
        if (s >= 100) {
            /*
             * For n = 9, m = 1 the table in section 10 of [1] shows
             *     s' = floor(s / 10) = floor(s 1_717_986_919 / 2^34)
             *
             * sp10 = 10 s'
             * tp10 = 10 t'
             * upin    iff    u' = sp10 10^k in Rv
             * wpin    iff    w' = tp10 10^k in Rv
             * See section 9.3 of [1].
             */
            int sp10 = 10 * (int) (s * 1_717_986_919L >>> 34);
            int tp10 = sp10 + 10;
            boolean upin = vbl + out <= sp10 << 2;
            boolean wpin = (tp10 << 2) + out <= vbr;
            if (upin != wpin) {
                return toChars(str, index, upin ? sp10 : tp10, k);
            }
        }

        /*
         * 10 <= s < 100    or    s >= 100  and  u', w' not in Rv
         * uin    iff    u = s 10^k in Rv
         * win    iff    w = t 10^k in Rv
         * See section 9.3 of [1].
         */
        int t = s + 1;
        boolean uin = vbl + out <= s << 2;
        boolean win = (t << 2) + out <= vbr;
        if (uin != win) {
            /* Exactly one of u or w lies in Rv */
            return toChars(str, index, uin ? s : t, k + dk);
        }
        /*
         * Both u and w lie in Rv: determine the one closest to v.
         * See section 9.3 of [1].
         */
        int cmp = vb - (s + t << 1);
        return toChars(str, index, cmp < 0 || cmp == 0 && (s & 0x1) == 0 ? s : t, k + dk);
    }

    /*
     * Computes rop(cp g 2^(-95))
     * See appendix and figure 11 of [1].
     */
    private static int rop(long g, long cp) {
        long x1 = multiplyHigh(g, cp);
        long vbp = x1 >>> 31;
        return (int) (vbp | (x1 & MASK_32) + MASK_32 >>> 32);
    }

    /*
     * Formats the decimal f 10^e.
     */
    private int toChars(byte[] str, int index, int f, int e) {
        /*
         * For details not discussed here see section 10 of [1].
         *
         * Determine len such that
         *     10^(len-1) <= f < 10^len
         */
        int len = flog10pow2(Integer.SIZE - numberOfLeadingZeros(f));
        if (f >= pow10(len)) {
            len += 1;
        }

        /*
         * Let fp and ep be the original f and e, respectively.
         * Transform f and e to ensure
         *     10^(H-1) <= f < 10^H
         *     fp 10^ep = f 10^(e-H) = 0.f 10^e
         */
        f *= (int)pow10(H - len);
        e += len;

        /*
         * The toChars?() methods perform left-to-right digits extraction
         * using ints, provided that the arguments are limited to 8 digits.
         * Therefore, split the H = 9 digits of f into:
         *     h = the most significant digit of f
         *     l = the last 8, least significant digits of f
         *
         * For n = 9, m = 8 the table in section 10 of [1] shows
         *     floor(f / 10^8) = floor(1_441_151_881 f / 2^57)
         */
        int h = (int) (f * 1_441_151_881L >>> 57);
        int l = f - 100_000_000 * h;

        if (0 < e && e <= 7) {
            return toChars1(str, index, h, l, e);
        }
        if (-3 < e && e <= 0) {
            return toChars2(str, index, h, l, e);
        }
        return toChars3(str, index, h, l, e);
    }

    private int toChars1(byte[] str, int index, int h, int l, int e) {
        /*
         * 0 < e <= 7: plain format without leading zeroes.
         * Left-to-right digits extraction:
         * algorithm 1 in [3], with b = 10, k = 8, n = 28.
         */
        index = putDigit(str, index, h);
        int y = y(l);
        int t;
        int i = 1;
        for (; i < e; ++i) {
            t = 10 * y;
            index = putDigit(str, index, t >>> 28);
            y = t & MASK_28;
        }
        index = putChar(str, index, '.');
        for (; i <= 8; ++i) {
            t = 10 * y;
            index = putDigit(str, index, t >>> 28);
            y = t & MASK_28;
        }
        return removeTrailingZeroes(str, index);
    }

    private int toChars2(byte[] str, int index, int h, int l, int e) {
        /* -3 < e <= 0: plain format with leading zeroes */
        index = putDigit(str, index, 0);
        index = putChar(str, index, '.');
        for (; e < 0; ++e) {
            index = putDigit(str, index, 0);
        }
        index = putDigit(str, index, h);
        index = put8Digits(str, index, l);
        return removeTrailingZeroes(str, index);
    }

    private int toChars3(byte[] str, int index, int h, int l, int e) {
        /* -3 >= e | e > 7: computerized scientific notation */
        index = putDigit(str, index, h);
        index = putChar(str, index, '.');
        index = put8Digits(str, index, l);
        index = removeTrailingZeroes(str, index);
        return exponent(str, index, e - 1);
    }

    private int exponent(byte[] str, int index, int e) {
        index = putChar(str, index, 'E');
        if (e < 0) {
            index = putChar(str, index, '-');
            e = -e;
        }
        if (e < 10) {
            return putDigit(str, index, e);
        }
        /*
         * For n = 2, m = 1 the table in section 10 of [1] shows
         *     floor(e / 10) = floor(103 e / 2^10)
         */
        int d = e * 103 >>> 10;
        index = putDigit(str, index, d);
        return putDigit(str, index, e - 10 * d);
    }
}
