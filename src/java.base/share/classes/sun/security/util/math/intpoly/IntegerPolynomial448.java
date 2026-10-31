/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util.math.intpoly;

import java.math.BigInteger;

public final class IntegerPolynomial448 extends IntegerPolynomial {
    private static final int BITS_PER_LIMB = 56;
    private static final int NUM_LIMBS = 8;
    private static final int MAX_ADDS = 2;
    public static final BigInteger MODULUS = evaluateModulus();
    private static final long CARRY_ADD = 1L << (BITS_PER_LIMB - 1);
    private static final long LIMB_MASK = -1L >>> (64 - BITS_PER_LIMB);

    public static final IntegerPolynomial448 ONE =
            new IntegerPolynomial448();

    private IntegerPolynomial448() {
        super(BITS_PER_LIMB, NUM_LIMBS, MAX_ADDS, MODULUS);
    }

    private static BigInteger evaluateModulus() {
        BigInteger result = BigInteger.valueOf(2).pow(448);
        result = result.subtract(BigInteger.valueOf(2).pow(224));
        result = result.subtract(BigInteger.valueOf(1));

        return result;
    }

    /**
     * Reduces digit 'v' at limb position 'i' to a lower limb.
     *
     * @param limbs [in|out] the limbs to reduce in.
     * @param v [in] the digit to reduce to the lower limb.
     * @param i [in] the limbs to reduce from.
     */
    @Override
    protected void reduceIn(long[] limbs, long v, int i) {
        limbs[i - 4] += v;
        limbs[i - 8] += v;
    }

    /**
     * Carry from high order limb and reduce to the lower order limb.  Assumed
     * to be called two times to propagate the carries.
     *
     * @param limbs [in|out] the limbs to fully carry and reduce.
     */
    @Override
    protected void finalCarryReduceLast(long[] limbs) {
        long carry = limbs[7] >> BITS_PER_LIMB;

        limbs[7] -= carry << BITS_PER_LIMB;
        limbs[4] += carry;
        limbs[0] += carry;
    }

    /**
     * Carry in all positions and reduce high order limb.
     *
     * @param limbs [in|out] the limbs to carry and reduce.
     */
    protected void reduce(long[] limbs) {
        long carry, c8;

        // carry from position 6
        carry = (limbs[6] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[6] -= carry << BITS_PER_LIMB;
        limbs[7] += carry;
        // carry from position 7
        carry = (limbs[7] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[7] -= carry << BITS_PER_LIMB;
        c8 = carry;
        // reduce from postion 8
        limbs[4] += c8;
        limbs[0] += c8;
        // carry from position 0
        carry = (limbs[0] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[0] -= carry << BITS_PER_LIMB;
        limbs[1] += carry;
        // carry from position 1
        carry = (limbs[1] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[1] -= carry << BITS_PER_LIMB;
        limbs[2] += carry;
        // carry from position 2
        carry = (limbs[2] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[2] -= carry << BITS_PER_LIMB;
        limbs[3] += carry;
        // carry from position 3
        carry = (limbs[3] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[3] -= carry << BITS_PER_LIMB;
        limbs[4] += carry;
        // carry from position 4
        carry = (limbs[4] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[4] -= carry << BITS_PER_LIMB;
        limbs[5] += carry;
        // carry from position 5
        carry = (limbs[5] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[5] -= carry << BITS_PER_LIMB;
        limbs[6] += carry;
        // carry from position 6
        carry = (limbs[6] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[6] -= carry << BITS_PER_LIMB;
        limbs[7] += carry;
    }

    /**
     * Multiply two limbs using a high/low digit technique that allows for
     * larger limb sizes.  It is assumed that both limbs have already been
     * reduced.
     *
     * @param a [in] the limb operand to multiply.
     * @param b [in] the limb operand to multiply.
     * @param r [out] the product of the limbs operands that is fully reduced.
     */
    protected void mult(long[] a, long[] b, long[] r) {
        long aa0 = a[0];
        long aa1 = a[1];
        long aa2 = a[2];
        long aa3 = a[3];
        long aa4 = a[4];
        long aa5 = a[5];
        long aa6 = a[6];
        long aa7 = a[7];

        long bb0 = b[0];
        long bb1 = b[1];
        long bb2 = b[2];
        long bb3 = b[3];
        long bb4 = b[4];
        long bb5 = b[5];
        long bb6 = b[6];
        long bb7 = b[7];

        final long shift1 = 64 - BITS_PER_LIMB;
        final long shift2 = BITS_PER_LIMB;

        // low digits from multiplication
        long d0, d1, d2, d3, d4, d5, d6, d7;
        // high digits from multiplication
        long dd0, dd1, dd2, dd3, dd4, dd5, dd6, dd7;
        // multiplication result digits for each column
        long c0, c1, c2, c3, c4, c5, c6, c7;
        long c8, c9, c10, c11, c12, c13, c14, c15;

        // Row 0 - multiply by aa0
        d0 = aa0 * bb0;
        dd0 = Math.multiplyHigh(aa0, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        d1 = aa0 * bb1;
        dd1 = Math.multiplyHigh(aa0, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa0 * bb2;
        dd2 = Math.multiplyHigh(aa0, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa0 * bb3;
        dd3 = Math.multiplyHigh(aa0, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa0 * bb4;
        dd4 = Math.multiplyHigh(aa0, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa0 * bb5;
        dd5 = Math.multiplyHigh(aa0, bb5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa0 * bb6;
        dd6 = Math.multiplyHigh(aa0, bb6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa0 * bb7;
        dd7 = Math.multiplyHigh(aa0, bb7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c0 = d0;
        c1 = d1 + dd0;
        c2 = d2 + dd1;
        c3 = d3 + dd2;
        c4 = d4 + dd3;
        c5 = d5 + dd4;
        c6 = d6 + dd5;
        c7 = d7 + dd6;
        c8 = dd7;

        // Row 1 - multiply by aa1
        d0 = aa1 * bb0;
        dd0 = Math.multiplyHigh(aa1, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        d1 = aa1 * bb1;
        dd1 = Math.multiplyHigh(aa1, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa1 * bb2;
        dd2 = Math.multiplyHigh(aa1, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa1 * bb3;
        dd3 = Math.multiplyHigh(aa1, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa1 * bb4;
        dd4 = Math.multiplyHigh(aa1, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa1 * bb5;
        dd5 = Math.multiplyHigh(aa1, bb5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa1 * bb6;
        dd6 = Math.multiplyHigh(aa1, bb6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa1 * bb7;
        dd7 = Math.multiplyHigh(aa1, bb7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c1 += d0;
        c2 += d1 + dd0;
        c3 += d2 + dd1;
        c4 += d3 + dd2;
        c5 += d4 + dd3;
        c6 += d5 + dd4;
        c7 += d6 + dd5;
        c8 += d7 + dd6;
        c9 = dd7;

        // Row 2 - multiply by aa2
        d0 = aa2 * bb0;
        dd0 = Math.multiplyHigh(aa2, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        d1 = aa2 * bb1;
        dd1 = Math.multiplyHigh(aa2, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa2 * bb2;
        dd2 = Math.multiplyHigh(aa2, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa2 * bb3;
        dd3 = Math.multiplyHigh(aa2, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa2 * bb4;
        dd4 = Math.multiplyHigh(aa2, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa2 * bb5;
        dd5 = Math.multiplyHigh(aa2, bb5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa2 * bb6;
        dd6 = Math.multiplyHigh(aa2, bb6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa2 * bb7;
        dd7 = Math.multiplyHigh(aa2, bb7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c2 += d0;
        c3 += d1 + dd0;
        c4 += d2 + dd1;
        c5 += d3 + dd2;
        c6 += d4 + dd3;
        c7 += d5 + dd4;
        c8 += d6 + dd5;
        c9 += d7 + dd6;
        c10 = dd7;

        // Row 3 - multiply by aa3
        d0 = aa3 * bb0;
        dd0 = Math.multiplyHigh(aa3, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        d1 = aa3 * bb1;
        dd1 = Math.multiplyHigh(aa3, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa3 * bb2;
        dd2 = Math.multiplyHigh(aa3, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa3 * bb3;
        dd3 = Math.multiplyHigh(aa3, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa3 * bb4;
        dd4 = Math.multiplyHigh(aa3, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa3 * bb5;
        dd5 = Math.multiplyHigh(aa3, bb5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa3 * bb6;
        dd6 = Math.multiplyHigh(aa3, bb6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa3 * bb7;
        dd7 = Math.multiplyHigh(aa3, bb7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c3  += d0;
        c4  += d1 + dd0;
        c5  += d2 + dd1;
        c6  += d3 + dd2;
        c7  += d4 + dd3;
        c8  += d5 + dd4;
        c9  += d6 + dd5;
        c10 += d7 + dd6;
        c11 = dd7;

        // Row 4 - multiply by aa4
        d0 = aa4 * bb0;
        dd0 = Math.multiplyHigh(aa4, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        d1 = aa4 * bb1;
        dd1 = Math.multiplyHigh(aa4, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa4 * bb2;
        dd2 = Math.multiplyHigh(aa4, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa4 * bb3;
        dd3 = Math.multiplyHigh(aa4, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa4 * bb4;
        dd4 = Math.multiplyHigh(aa4, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa4 * bb5;
        dd5 = Math.multiplyHigh(aa4, bb5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa4 * bb6;
        dd6 = Math.multiplyHigh(aa4, bb6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa4 * bb7;
        dd7 = Math.multiplyHigh(aa4, bb7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c4  += d0;
        c5  += d1 + dd0;
        c6  += d2 + dd1;
        c7  += d3 + dd2;
        c8  += d4 + dd3;
        c9  += d5 + dd4;
        c10 += d6 + dd5;
        c11 += d7 + dd6;
        c12 = dd7;

        // Row 5 - multiply by aa5
        d0 = aa5 * bb0;
        dd0 = Math.multiplyHigh(aa5, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        d1 = aa5 * bb1;
        dd1 = Math.multiplyHigh(aa5, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa5 * bb2;
        dd2 = Math.multiplyHigh(aa5, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa5 * bb3;
        dd3 = Math.multiplyHigh(aa5, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa5 * bb4;
        dd4 = Math.multiplyHigh(aa5, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa5 * bb5;
        dd5 = Math.multiplyHigh(aa5, bb5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa5 * bb6;
        dd6 = Math.multiplyHigh(aa5, bb6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa5 * bb7;
        dd7 = Math.multiplyHigh(aa5, bb7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c5  += d0;
        c6  += d1 + dd0;
        c7  += d2 + dd1;
        c8  += d3 + dd2;
        c9  += d4 + dd3;
        c10 += d5 + dd4;
        c11 += d6 + dd5;
        c12 += d7 + dd6;
        c13 = dd7;

        // Row 6 - multiply by aa6
        d0 = aa6 * bb0;
        dd0 = Math.multiplyHigh(aa6, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        d1 = aa6 * bb1;
        dd1 = Math.multiplyHigh(aa6, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa6 * bb2;
        dd2 = Math.multiplyHigh(aa6, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa6 * bb3;
        dd3 = Math.multiplyHigh(aa6, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa6 * bb4;
        dd4 = Math.multiplyHigh(aa6, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa6 * bb5;
        dd5 = Math.multiplyHigh(aa6, bb5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa6 * bb6;
        dd6 = Math.multiplyHigh(aa6, bb6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa6 * bb7;
        dd7 = Math.multiplyHigh(aa6, bb7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c6  += d0;
        c7  += d1 + dd0;
        c8  += d2 + dd1;
        c9  += d3 + dd2;
        c10 += d4 + dd3;
        c11 += d5 + dd4;
        c12 += d6 + dd5;
        c13 += d7 + dd6;
        c14 = dd7;

        // Row 7 - multiply by aa7
        d0 = aa7 * bb0;
        dd0 = Math.multiplyHigh(aa7, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        d1 = aa7 * bb1;
        dd1 = Math.multiplyHigh(aa7, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa7 * bb2;
        dd2 = Math.multiplyHigh(aa7, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa7 * bb3;
        dd3 = Math.multiplyHigh(aa7, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa7 * bb4;
        dd4 = Math.multiplyHigh(aa7, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa7 * bb5;
        dd5 = Math.multiplyHigh(aa7, bb5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa7 * bb6;
        dd6 = Math.multiplyHigh(aa7, bb6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa7 * bb7;
        dd7 = Math.multiplyHigh(aa7, bb7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c7  += d0;
        c8  += d1 + dd0;
        c9  += d2 + dd1;
        c10 += d3 + dd2;
        c11 += d4 + dd3;
        c12 += d5 + dd4;
        c13 += d6 + dd5;
        c14 += d7 + dd6;
        c15 = dd7;

        // Perform reduction for both terms
        r[0] = c0 + c8 + c12;
        r[1] = c1 + c9 + c13;
        r[2] = c2 + c10 + c14;
        r[3] = c3 + c11 + c15;
        r[4] = c4 + c8 + (c12 << 1);
        r[5] = c5 + c9 + (c13 << 1);
        r[6] = c6 + c10 + (c14 << 1);
        r[7] = c7 + c11 + (c15 << 1);

        reduce(r);
    }

    /**
     * Carry from a range of limb positions.
     * Override for performance (unnesting).
     *
     * @param limbs [in|out] the limbs for carry operation.
     * @param start [in] the starting position of carry.
     * @param end [in] the ending position of carry.
     */
    @Override
    protected void carry(long[] limbs, int start, int end) {
        long carry;

        for (int i = start; i < end; i++) {
            carry = (limbs[i] + CARRY_ADD) >> BITS_PER_LIMB;
            limbs[i] -= (carry << BITS_PER_LIMB);
            limbs[i + 1] += carry;
        }
    }

    /**
     * Carry operation for all limb positions.
     * Override for performance (unroll and unnesting).
     *
     * @param limbs [in|out] the limbs for carry operation.
     */
    @Override
    protected void carry(long[] limbs) {
        long carry = (limbs[0] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[0] -= carry << BITS_PER_LIMB;
        limbs[1] += carry;

        carry = (limbs[1] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[1] -= carry << BITS_PER_LIMB;
        limbs[2] += carry;

        carry = (limbs[2] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[2] -= carry << BITS_PER_LIMB;
        limbs[3] += carry;

        carry = (limbs[3] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[3] -= carry << BITS_PER_LIMB;
        limbs[4] += carry;

        carry = (limbs[4] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[4] -= carry << BITS_PER_LIMB;
        limbs[5] += carry;

        carry = (limbs[5] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[5] -= carry << BITS_PER_LIMB;
        limbs[6] += carry;

        carry = (limbs[6] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[6] -= carry << BITS_PER_LIMB;
        limbs[7] += carry;
    }

    /*
     * Multiply limbs by scalar value.
     * Superclass assumes that limb primitive radix > (bits per limb * 2)
     *
     * @param a [in|out] the limbs to multiply a carry operation. 'a' is
     * assumed to be reduced.
     * @param b [in] the scalar value to be muliplied with the limbs.
     */
    @Override
    protected void multByInt(long[] a, long b) {
        long aa0 = a[0];
        long aa1 = a[1];
        long aa2 = a[2];
        long aa3 = a[3];
        long aa4 = a[4];
        long aa5 = a[5];
        long aa6 = a[6];
        long aa7 = a[7];
        long bb0 = b;
        final long shift1 = 64 - BITS_PER_LIMB;
        final long shift2 = BITS_PER_LIMB;
        long d0;      // low digit from multiplication
        long dd0;     // high digit from multiplication
        // multiplication result digits for each column
        long c0, c1, c2, c3, c4, c5, c6, c7, c8;

        // Row 0 - multiply by aa0
        d0 = aa0 * bb0;
        dd0 = Math.multiplyHigh(aa0, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c0 = d0;
        c1 = dd0;

        // Row 1 - multiply by aa1
        d0 = aa1 * bb0;
        dd0 = Math.multiplyHigh(aa1, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c1 += d0;
        c2 = dd0;

        // Row 2 - multiply by aa2
        d0 = aa2 * bb0;
        dd0 = Math.multiplyHigh(aa2, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c2 += d0;
        c3 = dd0;

        // Row 3 - multiply by aa3
        d0 = aa3 * bb0;
        dd0 = Math.multiplyHigh(aa3, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c3 += d0;
        c4 = dd0;

        // Row 4 - multiply by aa4
        d0 = aa4 * bb0;
        dd0 = Math.multiplyHigh(aa4, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c4 += d0;
        c5 = dd0;

        // Row 5 - multiply by aa5
        d0 = aa5 * bb0;
        dd0 = Math.multiplyHigh(aa5, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c5 += d0;
        c6 = dd0;

        // Row 6 - multiply by aa6
        d0 = aa6 * bb0;
        dd0 = Math.multiplyHigh(aa6, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c6 += d0;
        c7 = dd0;

        // Row 7 - multiply by aa7
        d0 = aa7 * bb0;
        dd0 = Math.multiplyHigh(aa7, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c7 += d0;
        c8 = dd0;

        // Perform reduction from high limb
        a[0] = c0 + c8;
        a[1] = c1;
        a[2] = c2;
        a[3] = c3;
        a[4] = c4 + c8;
        a[5] = c5;
        a[6] = c6;
        a[7] = c7;

        reduce(a);
    }

    /**
     * Takes a single limb and squares it using a high/low digit technique that
     * allows for larger limb sizes.  It is assumed that the limb input has
     * already been reduced.
     *
     * @param a [in] the limb operand to square.
     * @param r [out] the resulting square of the limb which is fully reduced.
     */
    protected void square(long[] a, long[] r) {
        long aa0 = a[0];
        long aa1 = a[1];
        long aa2 = a[2];
        long aa3 = a[3];
        long aa4 = a[4];
        long aa5 = a[5];
        long aa6 = a[6];
        long aa7 = a[7];
        final long shift1 = 64 - BITS_PER_LIMB;
        final long shift2 = BITS_PER_LIMB;
        // low digits from multiplication
        long d0, d1, d2, d3, d4, d5, d6, d7;
        // high digits from multiplication
        long dd0, dd1, dd2, dd3, dd4, dd5, dd6, dd7;
        // multiplication result digits for each column
        long c0, c1, c2, c3, c4, c5, c6, c7, c8;
        long c9, c10, c11, c12, c13, c14, c15;

        // Row 0 - multiply by aa0
        d0 = aa0 * aa0;
        dd0 = Math.multiplyHigh(aa0, aa0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        d1 = aa0 * aa1;
        dd1 = Math.multiplyHigh(aa0, aa1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa0 * aa2;
        dd2 = Math.multiplyHigh(aa0, aa2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa0 * aa3;
        dd3 = Math.multiplyHigh(aa0, aa3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa0 * aa4;
        dd4 = Math.multiplyHigh(aa0, aa4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa0 * aa5;
        dd5 = Math.multiplyHigh(aa0, aa5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa0 * aa6;
        dd6 = Math.multiplyHigh(aa0, aa6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa0 * aa7;
        dd7 = Math.multiplyHigh(aa0, aa7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c0 = d0;
        c1 = (d1 << 1) + dd0;
        c2 = (d2 + dd1) << 1;
        c3 = (d3 + dd2) << 1;
        c4 = (d4 + dd3) << 1;
        c5 = (d5 + dd4) << 1;
        c6 = (d6 + dd5) << 1;
        c7 = (d7 + dd6) << 1;
        c8 = dd7 << 1;

        // Row 1 - multiply by aa1
        d1 = aa1 * aa1;
        dd1 = Math.multiplyHigh(aa1, aa1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;

        d2 = aa1 * aa2;
        dd2 = Math.multiplyHigh(aa1, aa2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa1 * aa3;
        dd3 = Math.multiplyHigh(aa1, aa3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa1 * aa4;
        dd4 = Math.multiplyHigh(aa1, aa4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa1 * aa5;
        dd5 = Math.multiplyHigh(aa1, aa5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa1 * aa6;
        dd6 = Math.multiplyHigh(aa1, aa6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa1 * aa7;
        dd7 = Math.multiplyHigh(aa1, aa7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c2 += d1;
        c3 += (d2 << 1) + dd1;
        c4 += (d3 + dd2) << 1;
        c5 += (d4 + dd3) << 1;
        c6 += (d5 + dd4) << 1;
        c7 += (d6 + dd5) << 1;
        c8 += (d7 + dd6) << 1;
        c9 = dd7 << 1;

        // Row 2 - multiply by aa2
        d2 = aa2 * aa2;
        dd2 = Math.multiplyHigh(aa2, aa2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;

        d3 = aa2 * aa3;
        dd3 = Math.multiplyHigh(aa2, aa3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa2 * aa4;
        dd4 = Math.multiplyHigh(aa2, aa4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa2 * aa5;
        dd5 = Math.multiplyHigh(aa2, aa5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa2 * aa6;
        dd6 = Math.multiplyHigh(aa2, aa6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa2 * aa7;
        dd7 = Math.multiplyHigh(aa2, aa7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c4 += d2;
        c5 += (d3 << 1) + dd2;
        c6 += (d4 + dd3) << 1;
        c7 += (d5 + dd4) << 1;
        c8 += (d6 + dd5) << 1;
        c9 += (d7 + dd6) << 1;
        c10 = dd7 << 1;

        // Row 3 - multiply by aa3
        d3 = aa3 * aa3;
        dd3 = Math.multiplyHigh(aa3, aa3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;

        d4 = aa3 * aa4;
        dd4 = Math.multiplyHigh(aa3, aa4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa3 * aa5;
        dd5 = Math.multiplyHigh(aa3, aa5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa3 * aa6;
        dd6 = Math.multiplyHigh(aa3, aa6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa3 * aa7;
        dd7 = Math.multiplyHigh(aa3, aa7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c6  += d3;
        c7  += (d4 << 1) + dd3;
        c8  += (d5 + dd4) << 1;
        c9  += (d6 + dd5) << 1;
        c10 += (d7 + dd6) << 1;
        c11 = dd7 << 1;

        // Row 4 - multiply by aa4
        d4 = aa4 * aa4;
        dd4 = Math.multiplyHigh(aa4, aa4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        d5 = aa4 * aa5;
        dd5 = Math.multiplyHigh(aa4, aa5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa4 * aa6;
        dd6 = Math.multiplyHigh(aa4, aa6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa4 * aa7;
        dd7 = Math.multiplyHigh(aa4, aa7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c8  += d4;
        c9  += (d5 << 1) + dd4;
        c10 += (d6 + dd5) << 1;
        c11 += (d7 + dd6) << 1;
        c12 = dd7 << 1;

        // Row 5 - multiply by aa5
        d5 = aa5 * aa5;
        dd5 = Math.multiplyHigh(aa5, aa5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa5 * aa6;
        dd6 = Math.multiplyHigh(aa5, aa6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa5 * aa7;
        dd7 = Math.multiplyHigh(aa5, aa7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c10 += d5;
        c11 += (d6 << 1) + dd5;
        c12 += (d7 + dd6) << 1;
        c13 = dd7 << 1;

        // Row 6 - multiply by aa6
        d6 = aa6 * aa6;
        dd6 = Math.multiplyHigh(aa6, aa6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        d7 = aa6 * aa7;
        dd7 = Math.multiplyHigh(aa6, aa7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c12 += d6;
        c13 += (d7 << 1) + dd6;
        c14 = dd7 << 1;

        // Row7 - multiply by aa7
        d7 = aa7 * aa7;
        dd7 = Math.multiplyHigh(aa7, aa7) << shift1 | (d7 >>> shift2);
        d7 &= LIMB_MASK;

        c14 += d7;
        c15 = dd7;

        // Perform reduction for both terms
        r[0] = c0 + c8 + c12;
        r[1] = c1 + c9 + c13;
        r[2] = c2 + c10 + c14;
        r[3] = c3 + c11 + c15;
        r[4] = c4 + c8 + (c12 << 1);
        r[5] = c5 + c9 + (c13 << 1);
        r[6] = c6 + c10 + (c14 << 1);
        r[7] = c7 + c11 + (c15 << 1);

        reduce(r);
    }
}
