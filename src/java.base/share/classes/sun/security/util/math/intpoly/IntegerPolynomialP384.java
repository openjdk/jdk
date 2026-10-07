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
public final class IntegerPolynomialP384 extends IntegerPolynomial {
    private static final int BITS_PER_LIMB = 55;
    private static final int NUM_LIMBS = 7;
    private static final int MAX_ADDS = 2;
    public static final BigInteger MODULUS = evaluateModulus();
    private static final long CARRY_ADD = 1L << (BITS_PER_LIMB - 1);
    private static final long LIMB_MASK = -1L >>> (64 - BITS_PER_LIMB);

    public static final IntegerPolynomialP384 ONE = new IntegerPolynomialP384();

    private IntegerPolynomialP384() {
        super(BITS_PER_LIMB, NUM_LIMBS, MAX_ADDS, MODULUS);
    }

    private static BigInteger evaluateModulus() {
        BigInteger result = BigInteger.valueOf(2).pow(384);

        result = result.subtract(BigInteger.valueOf(1).shiftLeft(128));
        result = result.subtract(BigInteger.valueOf(1).shiftLeft(96));
        result = result.add(BigInteger.valueOf(1).shiftLeft(32));
        result = result.subtract(BigInteger.valueOf(1));

        return result;
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
    }

    /**
     * Reduces digit 'v' at limb position 'i' to a lower limb.
     *
     * @param limbs [in|out] the limbs to reduce in.
     * @param v [in] the digit to reduce to the lower limb.
     * @param i [in] the limbs to reduce from.
     */
    protected void reduceIn(long[] limbs, long v, int i) {
        limbs[i - 5] += (v << 19) & LIMB_MASK;
        limbs[i - 4] += v >> 36;
        limbs[i - 6] += (v << 42) & LIMB_MASK;
        limbs[i - 5] += v >> 13;
        limbs[i - 7] -= (v << 33) & LIMB_MASK;
        limbs[i - 6] -= v >> 22;
        limbs[i - 7] += (v << 1) & LIMB_MASK;
        limbs[i - 6] += v >> 54;
    }

    /**
     * Carry from high order limb and reduce to the lower order limb.  Assumed
     * to be called two times to propagate the carries.
     *
     * @param limbs [in|out] the limbs to fully carry and reduce.
     */
    protected void finalCarryReduceLast(long[] limbs) {
        long carry = limbs[6] >> 54;

        limbs[6] -= carry << 54;

        limbs[2] += (carry << 18) & LIMB_MASK;
        limbs[3] += carry >> 37;

        limbs[1] += (carry << 41) & LIMB_MASK;
        limbs[2] += carry >> 14;

        limbs[0] -= (carry << 32) & LIMB_MASK;
        limbs[1] -= carry >> 23;

        limbs[0] += carry;
    }

    private void carryReduceUpper(long[] c) {
        // reduce from position 13
        c[8] += (c[13] << 19) & LIMB_MASK;
        c[9] += c[13] >> 36;
        c[7] += (c[13] << 42) & LIMB_MASK;
        c[8] += c[13] >> 13;
        c[6] -= (c[13] << 33) & LIMB_MASK;
        c[7] -= c[13] >> 22;
        c[6] += (c[13] << 1) & LIMB_MASK;
        c[7] += c[13] >> 54;
        // reduce from position 12
        c[7] += (c[12] << 19) & LIMB_MASK;
        c[8] += c[12] >> 36;
        c[6] += (c[12] << 42) & LIMB_MASK;
        c[7] += c[12] >> 13;
        c[5] -= (c[12] << 33) & LIMB_MASK;
        c[6] -= c[12] >> 22;
        c[5] += (c[12] << 1) & LIMB_MASK;
        c[6] += c[12] >> 54;
        // reduce from position 11
        c[6] += (c[11] << 19) & LIMB_MASK;
        c[7] += c[11] >> 36;
        c[5] += (c[11] << 42) & LIMB_MASK;
        c[6] += c[11] >> 13;
        c[4] -= (c[11] << 33) & LIMB_MASK;
        c[5] -= c[11] >> 22;
        c[4] += (c[11] << 1) & LIMB_MASK;
        c[5] += c[11] >> 54;
        // reduce from position 10
        c[5] += (c[10] << 19) & LIMB_MASK;
        c[6] += c[10] >> 36;
        c[4] += (c[10] << 42) & LIMB_MASK;
        c[5] += c[10] >> 13;
        c[3] -= (c[10] << 33) & LIMB_MASK;
        c[4] -= c[10] >> 22;
        c[3] += (c[10] << 1) & LIMB_MASK;
        c[4] += c[10] >> 54;
        // reduce from position 9
        c[4] += (c[9] << 19) & LIMB_MASK;
        c[5] += c[9] >> 36;
        c[3] += (c[9] << 42) & LIMB_MASK;
        c[4] += c[9] >> 13;
        c[2] -= (c[9] << 33) & LIMB_MASK;
        c[3] -= c[9] >> 22;
        c[2] += (c[9] << 1) & LIMB_MASK;
        c[3] += c[9] >> 54;
        // reduce from position 8
        c[3] += (c[8] << 19) & LIMB_MASK;
        c[4] += c[8] >> 36;
        c[2] += (c[8] << 42) & LIMB_MASK;
        c[3] += c[8] >> 13;
        c[1] -= (c[8] << 33) & LIMB_MASK;
        c[2] -= c[8] >> 22;
        c[1] += (c[8] << 1) & LIMB_MASK;
        c[2] += c[8] >> 54;
        // reduce from position 7
        c[2] += (c[7] << 19) & LIMB_MASK;
        c[3] += c[7] >> 36;
        c[1] += (c[7] << 42) & LIMB_MASK;
        c[2] += c[7] >> 13;
        c[0] -= (c[7] << 33) & LIMB_MASK;
        c[1] -= c[7] >> 22;
        c[0] += (c[7] << 1) & LIMB_MASK;
        c[1] += c[7] >> 54;

        carryReduceLower(c);
    }

    private void carryReduceLower(long[] c) {
        long carry, c7;

        // carry from position 5
        carry = (c[5] + CARRY_ADD) >> BITS_PER_LIMB;
        c[5] -= carry << BITS_PER_LIMB;
        c[6] += carry;
        // carry from position 6
        carry = (c[6] + CARRY_ADD) >> BITS_PER_LIMB;
        c[6] -= carry << BITS_PER_LIMB;
        c7 = carry;
        // reduce from position 7
        c[2] += (c7 << 19) & LIMB_MASK;
        c[3] += c7 >> 36;
        c[1] += (c7 << 42) & LIMB_MASK;
        c[2] += c7 >> 13;
        c[0] -= (c7 << 33) & LIMB_MASK;
        c[1] -= c7 >> 22;
        c[0] += (c7 << 1) & LIMB_MASK;
        c[1] += c7 >> 54;
        // carry from position 0
        carry = (c[0] + CARRY_ADD) >> BITS_PER_LIMB;
        c[0] -= carry << BITS_PER_LIMB;
        c[1] += carry;
        // carry from position 1
        carry = (c[1] + CARRY_ADD) >> BITS_PER_LIMB;
        c[1] -= carry << BITS_PER_LIMB;
        c[2] += carry;
        // carry from position 2
        carry = (c[2] + CARRY_ADD) >> BITS_PER_LIMB;
        c[2] -= carry << BITS_PER_LIMB;
        c[3] += carry;
        // carry from position 3
        carry = (c[3] + CARRY_ADD) >> BITS_PER_LIMB;
        c[3] -= carry << BITS_PER_LIMB;
        c[4] += carry;
        // carry from position 4
        carry = (c[4] + CARRY_ADD) >> BITS_PER_LIMB;
        c[4] -= carry << BITS_PER_LIMB;
        c[5] += carry;
        // carry from position 5
        carry = (c[5] + CARRY_ADD) >> BITS_PER_LIMB;
        c[5] -= carry << BITS_PER_LIMB;
        c[6] += carry;
    }

    @Override
    protected void mult(long[] a, long[] b, long[] r) {
        // multiplication result digits for each column
        long[] c = new long[2 * NUM_LIMBS];

        long aa0 = a[0];
        long aa1 = a[1];
        long aa2 = a[2];
        long aa3 = a[3];
        long aa4 = a[4];
        long aa5 = a[5];
        long aa6 = a[6];

        long bb0 = b[0];
        long bb1 = b[1];
        long bb2 = b[2];
        long bb3 = b[3];
        long bb4 = b[4];
        long bb5 = b[5];
        long bb6 = b[6];

        final long shift1 = 64 - BITS_PER_LIMB;
        final long shift2 = BITS_PER_LIMB;

        // low digits from multiplication
        long d0, d1, d2, d3, d4, d5, d6;
        // high digits from multiplication
        long dd0, dd1, dd2, dd3, dd4, dd5, dd6;

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

        c[0] = d0;
        c[1] = d1 + dd0;
        c[2] = d2 + dd1;
        c[3] = d3 + dd2;
        c[4] = d4 + dd3;
        c[5] = d5 + dd4;
        c[6] = d6 + dd5;
        c[7] = dd6;

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

        c[1] += d0;
        c[2] += d1 + dd0;
        c[3] += d2 + dd1;
        c[4] += d3 + dd2;
        c[5] += d4 + dd3;
        c[6] += d5 + dd4;
        c[7] += d6 + dd5;
        c[8]  = dd6;

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

        c[2] += d0;
        c[3] += d1 + dd0;
        c[4] += d2 + dd1;
        c[5] += d3 + dd2;
        c[6] += d4 + dd3;
        c[7] += d5 + dd4;
        c[8] += d6 + dd5;
        c[9]  = dd6;

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

        c[3]  += d0;
        c[4]  += d1 + dd0;
        c[5]  += d2 + dd1;
        c[6]  += d3 + dd2;
        c[7]  += d4 + dd3;
        c[8]  += d5 + dd4;
        c[9]  += d6 + dd5;
        c[10]  = dd6;

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

        c[4]  += d0;
        c[5]  += d1 + dd0;
        c[6]  += d2 + dd1;
        c[7]  += d3 + dd2;
        c[8]  += d4 + dd3;
        c[9]  += d5 + dd4;
        c[10] += d6 + dd5;
        c[11]  = dd6;

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

        c[5]  += d0;
        c[6]  += d1 + dd0;
        c[7]  += d2 + dd1;
        c[8]  += d3 + dd2;
        c[9]  += d4 + dd3;
        c[10] += d5 + dd4;
        c[11] += d6 + dd5;
        c[12]  = dd6;

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

        c[6]  += d0;
        c[7]  += d1 + dd0;
        c[8]  += d2 + dd1;
        c[9]  += d3 + dd2;
        c[10] += d4 + dd3;
        c[11] += d5 + dd4;
        c[12] += d6 + dd5;
        c[13]  = dd6;

        carryReduceUpper(c);

        r[0] = c[0];
        r[1] = c[1];
        r[2] = c[2];
        r[3] = c[3];
        r[4] = c[4];
        r[5] = c[5];
        r[6] = c[6];
    }

    /**
     * Carry in all positions and reduce high order limb.
     *
     * @param limbs [in|out] the limbs to carry and reduce.
     */
    protected void reduce(long[] limbs) {
        carryReduceLower(limbs);
/*
        long carry = (limbs[3] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[3] -= carry << BITS_PER_LIMB;
        limbs[4] += carry;

        carry = (limbs[4] + CARRY_ADD) >> BITS_PER_LIMB;
        limbs[4] -= carry << BITS_PER_LIMB;

        limbs[0] += 19 * carry;

        carry = (limbs[0] + CARRY_ADD) >> BITS_PER_LIMB;
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
*/
    }

    /**
     * Multiply limbs by scalar value.
     * Superclass assumes that limb primitive radix > (bits per limb * 2)
     *
     * @param a [in|out] the limbs to multiply a carry operation. 'a' is
     * assumed to be reduced.
     * @param b [in] the scalar value to be muliplied with the limbs.
     */
    @Override
    protected void multByInt(long[] a, long b) {
        long[] ba = new long[NUM_LIMBS];
        long aa0 = a[0];
        long aa1 = a[1];
        long aa2 = a[2];
        long aa3 = a[3];
        long aa4 = a[4];
        long aa5 = a[5];
        long aa6 = a[6];
        long bb0 = b;
        final long shift1 = 64 - BITS_PER_LIMB;
        final long shift2 = BITS_PER_LIMB;
        long d0;      // low digit from multiplication
        long dd0;     // high digit from multiplication
        // multiplication result digits for each column
        long[] c = new long[NUM_LIMBS + 1];

        // Row 0 - multiply by aa0
        d0 = aa0 * bb0;
        dd0 = Math.multiplyHigh(aa0, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c[0] = d0;
        c[1] = dd0;

        // Row 1 - multiply by aa1
        d0 = aa1 * bb0;
        dd0 = Math.multiplyHigh(aa1, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c[1] += d0;
        c[2] = dd0;

        // Row 2 - multiply by aa2
        d0 = aa2 * bb0;
        dd0 = Math.multiplyHigh(aa2, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c[2] += d0;
        c[3] = dd0;

        // Row 3 - multiply by aa3
        d0 = aa3 * bb0;
        dd0 = Math.multiplyHigh(aa3, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c[3] += d0;
        c[4] = dd0;

        // Row 4 - multiply by aa4
        d0 = aa4 * bb0;
        dd0 = Math.multiplyHigh(aa4, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c[4] += d0;
        c[5] = dd0;

        // Row 5 - multiply by aa5
        d0 = aa5 * bb0;
        dd0 = Math.multiplyHigh(aa5, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c[5] += d0;
        c[6] = dd0;

        // Row 6 - multiply by aa6
        d0 = aa6 * bb0;
        dd0 = Math.multiplyHigh(aa6, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;

        c[6] += d0;
        c[7] = dd0;

        // Reduce single high-order limb before reduce/carry the rest
        reduceIn(c, c[7], 7);
        reduce(c);

        a[0] = c[0];
        a[1] = c[1];
        a[2] = c[2];
        a[3] = c[3];
        a[4] = c[4];
        a[5] = c[5];
        a[6] = c[6];
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
        final long shift1 = 64 - BITS_PER_LIMB;
        final long shift2 = BITS_PER_LIMB;
        // low digits from multiplication
        long d0, d1, d2, d3, d4, d5, d6;
        // high digits from multiplication
        long dd0, dd1, dd2, dd3, dd4, dd5, dd6;
        // multiplication result digits for each column
        long[] c = new long[NUM_LIMBS * 2];

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

        c[0] = d0;
        c[1] = (d1 << 1) + dd0;
        c[2] = (d2 + dd1) << 1;
        c[3] = (d3 + dd2) << 1;
        c[4] = (d4 + dd3) << 1;
        c[5] = (d5 + dd4) << 1;
        c[6] = (d6 + dd5) << 1;
        c[7] = dd6 << 1;

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

        c[2] += d1;
        c[3] += (d2 << 1) + dd1;
        c[4] += (d3 + dd2) << 1;
        c[5] += (d4 + dd3) << 1;
        c[6] += (d5 + dd4) << 1;
        c[7] += (d6 + dd5) << 1;
        c[8] = dd6 << 1;

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

        c[4] += d2;
        c[5] += (d3 << 1) + dd2;
        c[6] += (d4 + dd3) << 1;
        c[7] += (d5 + dd4) << 1;
        c[8] += (d6 + dd5) << 1;
        c[9] = dd6 << 1;

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

        c[6]  += d3;
        c[7]  += (d4 << 1) + dd3;
        c[8]  += (d5 + dd4) << 1;
        c[9]  += (d6 + dd5) << 1;
        c[10] = dd6 << 1;

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

        c[8]  += d4;
        c[9]  += (d5 << 1) + dd4;
        c[10] += (d6 + dd5) << 1;
        c[11] = dd6;

        // Row 5 - multiply by aa4
        d5 = aa5 * aa5;
        dd5 = Math.multiplyHigh(aa5, aa5) << shift1 | (d5 >>> shift2);
        d5 &= LIMB_MASK;

        d6 = aa5 * aa6;
        dd6 = Math.multiplyHigh(aa5, aa6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        c[10] += d5;
        c[11] += (d6 << 1) + dd5;
        c[12] = dd6 << 1;

        // Row 6 - multiply by aa6
        d6 = aa6 * aa6;
        dd6 = Math.multiplyHigh(aa6, aa6) << shift1 | (d6 >>> shift2);
        d6 &= LIMB_MASK;

        c[12] += d6;
        c[13] = dd6;

        carryReduceUpper(c);

        r[0] = c[0];
        r[1] = c[1];
        r[2] = c[2];
        r[3] = c[3];
        r[4] = c[4];
        r[5] = c[5];
        r[6] = c[6];
    }
}
