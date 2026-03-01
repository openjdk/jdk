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
import jdk.internal.vm.annotation.IntrinsicCandidate;

public final class IntegerPolynomial25519 extends IntegerPolynomial {
    private static final int BITS_PER_LIMB = 51;
    private static final int NUM_LIMBS = 5;
    private static final int MAX_ADDS = 0;
    public static final BigInteger MODULUS = evaluateModulus();
    private static final long CARRY_ADD = 1L << (BITS_PER_LIMB - 1);
    private static final long LIMB_MASK = -1L >>> (64 - BITS_PER_LIMB);

    private static final long[] one = new long[] {
        0x0000000000000001L, 0x0000000000000000L, 0x0000000000000000L,
        0x0000000000000000L, 0x0000000000000000L };
    private static final long[] zero = new long[] {
        0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
        0x0000000000000000L, 0x0000000000000000L };

    public static final IntegerPolynomial25519 ONE =
            new IntegerPolynomial25519();

    private IntegerPolynomial25519() {
        super(BITS_PER_LIMB, NUM_LIMBS, MAX_ADDS, MODULUS);
    }

    private static BigInteger evaluateModulus() {
        BigInteger result = BigInteger.valueOf(2).pow(255);
        result = result.subtract(BigInteger.valueOf(19));
        return result;
    }

    @Override
    public ImmutableElement get0() {
        return new ImmutableElement(zero, 0);
    }

    @Override
    public ImmutableElement get1() {
        return new ImmutableElement(one, 0);
    }

    // Overriden for performance (unnesting)
    @Override
    protected void carry(long[] limbs, int start, int end) {
        long carry;

        for (int i = start; i < end; i++) {
            carry = (limbs[i] + CARRY_ADD) >> BITS_PER_LIMB;
            limbs[i] -= (carry << BITS_PER_LIMB);
            limbs[i + 1] += carry;
        }
    }

    // Overriden for performance (unroll and unnesting)
    @Override
    protected void carry(long[] limbs) {
        //carry(limbs, 0, limbs.length - 1);
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
    }

    // Superclass assumes that limb primitive radix > (bits per limb * 2)
    @Override
    protected void multByInt(long[] a, long b) {
        long[] blimbs = new long[a.length];

        blimbs[0] = b;
        mult(a, blimbs, a);
        reduce(a);
    }

    // Overriden for performance (unroll and unnesting)
    @Override
    protected void reduce(long[] limbs) {
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
    }

    @Override
    protected void reduceIn(long[] limbs, long v, int i) {
        limbs[i - NUM_LIMBS] += 19 * v;
    }

    @Override
    protected void finalCarryReduceLast(long[] limbs) {
        long carry = limbs[4] >> BITS_PER_LIMB;

        limbs[4] -= carry << BITS_PER_LIMB;
        limbs[0] += 19 * carry;
    }

    @Override
    @IntrinsicCandidate
    protected void mult(long[] a, long[] b, long[] r) {
        long aa0 = a[0];
        long aa1 = a[1];
        long aa2 = a[2];
        long aa3 = a[3];
        long aa4 = a[4];

        long bb0 = b[0];
        long bb1 = b[1];
        long bb2 = b[2];
        long bb3 = b[3];
        long bb4 = b[4];

        final long shift1 = 64 - BITS_PER_LIMB;
        final long shift2 = BITS_PER_LIMB;

        long d0, d1, d2, d3, d4;      // low digits from multiplication
        long dd0, dd1, dd2, dd3, dd4; // high digits from multiplication
        // multiplication result digits for each column
        long c0, c1, c2, c3, c4, c5, c6, c7, c8, c9;

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

        c0 = d0;
        c1 = d1 + dd0;
        c2 = d2 + dd1;
        c3 = d3 + dd2;
        c4 = d4 + dd3;
        c5 = dd4;

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

        c1 += d0;
        c2 += d1 + dd0;
        c3 += d2 + dd1;
        c4 += d3 + dd2;
        c5 += d4 + dd3;
        c6 = dd4;

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

        c2 += d0;
        c3 += d1 + dd0;
        c4 += d2 + dd1;
        c5 += d3 + dd2;
        c6 += d4 + dd3;
        c7 = dd4;

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

        c3 += d0;
        c4 += d1 + dd0;
        c5 += d2 + dd1;
        c6 += d3 + dd2;
        c7 += d4 + dd3;
        c8 = dd4;

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

        c4 += d0;
        c5 += d1 + dd0;
        c6 += d2 + dd1;
        c7 += d3 + dd2;
        c8 += d4 + dd3;
        c9 = dd4;

        // Perform pseudo-Mersenne reduction
        r[0] = c0 + (19 * c5);
        r[1] = c1 + (19 * c6);
        r[2] = c2 + (19 * c7);
        r[3] = c3 + (19 * c8);
        r[4] = c4 + (19 * c9);

        reduce(r);
    }

    @Override
    protected void square(long[] a, long[] r) {
        mult(a, a, r);
    }
}
