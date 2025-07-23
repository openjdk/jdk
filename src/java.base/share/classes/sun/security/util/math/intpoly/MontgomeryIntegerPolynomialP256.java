/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.util.math.ImmutableIntegerModuloP;
import sun.security.util.math.IntegerMontgomeryFieldModuloP;
import sun.security.util.math.SmallValue;
import sun.security.util.math.IntegerFieldModuloP;
import java.math.BigInteger;
import jdk.internal.vm.annotation.IntrinsicCandidate;
import jdk.internal.vm.annotation.ForceInline;

// Reference:
// - [1] Shay Gueron and Vlad Krasnov "Fast Prime Field Elliptic Curve
//       Cryptography with 256 Bit Primes"
//
public final class MontgomeryIntegerPolynomialP256 extends IntegerPolynomial
        implements IntegerMontgomeryFieldModuloP {
    private static final int BITS_PER_LIMB = 52;
    private static final int NUM_LIMBS = 5;
    private static final int MAX_ADDS = 0;
    public static final BigInteger MODULUS = evaluateModulus();
    private static final long LIMB_MASK = -1L >>> (64 - BITS_PER_LIMB);

    public static final MontgomeryIntegerPolynomialP256 ONE = new MontgomeryIntegerPolynomialP256();

    // h = 2^(2*260)%p = 0x4fffffffdfffffffffffffffefffffffbffffffff000000000000000300
    // oneActual = 1
    // oneMont = (1*2^260) mod p
    // modulus = p
    private static final long[] h = new long[] {
        0x0000000000000300L, 0x000ffffffff00000L, 0x000ffffefffffffbL,
        0x000fdfffffffffffL, 0x0000000004ffffffL };
    private static final long[] oneActual = new long[] {
        0x0000000000000001L, 0x0000000000000000L, 0x0000000000000000L,
        0x0000000000000000L, 0x0000000000000000L };
    private static final long[] oneMont = new long[] {
        0x0000000000000010L, 0x000f000000000000L, 0x000fffffffffffffL,
        0x000ffeffffffffffL, 0x00000000000fffffL };
    private static final long[] zero = new long[] {
        0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L,
        0x0000000000000000L, 0x0000000000000000L };
    private static final long[] modulus = new long[] {
        0x000fffffffffffffL, 0x00000fffffffffffL, 0x0000000000000000L,
        0x0000001000000000L, 0x0000ffffffff0000L };

    private MontgomeryIntegerPolynomialP256() {
        super(BITS_PER_LIMB, NUM_LIMBS, MAX_ADDS, MODULUS);
    }

    public IntegerFieldModuloP residueField() {
        return IntegerPolynomialP256.ONE;
    }

    // (224%nat,-1)::(192%nat,1)::(96%nat,1)::(0%nat,-1)::nil.
    private static BigInteger evaluateModulus() {
        BigInteger result = BigInteger.valueOf(2).pow(256);
        result = result.subtract(BigInteger.valueOf(1).shiftLeft(224));
        result = result.add(BigInteger.valueOf(1).shiftLeft(192));
        result = result.add(BigInteger.valueOf(1).shiftLeft(96));
        result = result.subtract(BigInteger.valueOf(1));
        return result;
    }

    @Override
    public ImmutableElement get0() {
        return new ImmutableElement(zero, 0);
    }

    // One in montgomery domain: (1*2^260) mod p
    @Override
    public ImmutableElement get1() {
        return new ImmutableElement(oneMont, 0);
    }

    // Convert v to Montgomery domain
    @Override
    public ImmutableElement getElement(BigInteger v) {
        long[] vLimbs = new long[NUM_LIMBS];
        long[] montLimbs = new long[NUM_LIMBS];
        setLimbsValuePositive(v, vLimbs);

        // Convert to Montgomery domain
        mult(vLimbs, h, montLimbs);
        return new ImmutableElement(montLimbs, 0);
    }

    @Override
    public SmallValue getSmallValue(int value) {
        // Explicitly here as reminder that SmallValue stays in residue domain
        // See multByInt below for how this is used
        return super.getSmallValue(value);
    }

    @Override
    public ImmutableIntegerModuloP fromMontgomery(ImmutableIntegerModuloP n) {
        assert n.getField() == MontgomeryIntegerPolynomialP256.ONE;

        ImmutableElement nn = (ImmutableElement) n;
        long[] r1 = new long[NUM_LIMBS];
        long[] r2 = new long[2 * NUM_LIMBS];
        long[] limbs = nn.getLimbs();
        reduce(limbs);
        MontgomeryIntegerPolynomialP256.ONE.mult(limbs, oneActual, r1);
        reduce(r1);
        halfLimbs(r1, r2);
        return IntegerPolynomialP256.ONE.new ImmutableElement(r2, 0);
    }

    private void halfLimbs(long[] a, long[] r) {
        final long HALF_BITS_LIMB = BITS_PER_LIMB / 2;
        final long HALF_LIMB_MASK = -1L >>> (64 - HALF_BITS_LIMB);
        r[0] = a[0] & HALF_LIMB_MASK;
        r[1] = a[0] >> HALF_BITS_LIMB;
        r[2] = a[1] & HALF_LIMB_MASK;
        r[3] = a[1] >> HALF_BITS_LIMB;
        r[4] = a[2] & HALF_LIMB_MASK;
        r[5] = a[2] >> HALF_BITS_LIMB;
        r[6] = a[3] & HALF_LIMB_MASK;
        r[7] = a[3] >> HALF_BITS_LIMB;
        r[8] = a[4] & HALF_LIMB_MASK;
        r[9] = a[4] >> HALF_BITS_LIMB;
    }

    @Override
    protected void square(long[] a, long[] r) {
        mult(a, a, r);
    }


    /**
     * Unrolled Word-by-Word Montgomery Multiplication r = a * b * 2^-260 (mod P)
     *
     * See [1] Figure 5. "Algorithm 2: Word-by-Word Montgomery Multiplication
     * for a Montgomery Friendly modulus p". Note: Step 6. Skipped; Instead use
     * numAdds to reuse existing overflow logic.
     */
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

        final long shift1 = 64 - BITS_PER_LIMB; // 12
        final long shift2 = BITS_PER_LIMB; // 40

        long d0, d1, d2, d3, d4;      // low digits from multiplication
        long dd0, dd1, dd2, dd3, dd4; // high digits from multiplication
        long n, n0, n1, n2, n3, n4,
            nn0, nn1, nn2, nn3, nn4; // modulus multiple digits
        long c0, c1, c2, c3, c4, c5, c6, c7, c8, c9; // multiplication result
                                                     // digits for each column

        // Row 0 - multiply by aa0 and reduce out c0
        d0 = aa0 * bb0;
        dd0 = Math.unsignedMultiplyHigh(aa0, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;
        n = d0;
        d1 = aa0 * bb1;
        dd1 = Math.unsignedMultiplyHigh(aa0, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;
        d2 = aa0 * bb2;
        dd2 = Math.unsignedMultiplyHigh(aa0, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;
        d3 = aa0 * bb3;
        dd3 = Math.unsignedMultiplyHigh(aa0, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;
        d4 = aa0 * bb4;
        dd4 = Math.unsignedMultiplyHigh(aa0, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        n0 = n * modulus[0];
        nn0 = Math.unsignedMultiplyHigh(n, modulus[0]) << shift1 | (n0 >>> shift2);
        n0 &= LIMB_MASK;
        n1 = n * modulus[1];
        nn1 = Math.unsignedMultiplyHigh(n, modulus[1]) << shift1 | (n1 >>> shift2);
        n1 &= LIMB_MASK;
        n2 = n * modulus[2];
        nn2 = Math.unsignedMultiplyHigh(n, modulus[2]) << shift1 | (n2 >>> shift2);
        n2 &= LIMB_MASK;
        n3 = n * modulus[3];
        nn3 = Math.unsignedMultiplyHigh(n, modulus[3]) << shift1 | (n3 >>> shift2);
        n3 &= LIMB_MASK;
        n4 = n * modulus[4];
        nn4 = Math.unsignedMultiplyHigh(n, modulus[4]) << shift1 | (n4 >>> shift2);
        n4 &= LIMB_MASK;

        dd0 += nn0;
        d0 += n0;
        dd1 += nn1;
        d1 += n1;
        dd2 += nn2;
        d2 += n2;
        dd3 += nn3;
        d3 += n3;
        dd4 += nn4;
        d4 += n4;

        c1 = d1 + dd0 + (d0 >>> BITS_PER_LIMB);
        c2 = d2 + dd1;
        c3 = d3 + dd2;
        c4 = d4 + dd3;
        c5 = dd4;

        // Row 1 - multiply by aa1 and reduce out c1
        d0 = aa1 * bb0;
        dd0 = Math.unsignedMultiplyHigh(aa1, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;
        d0 += c1;
        n = d0 & LIMB_MASK;
        d1 = aa1 * bb1;
        dd1 = Math.unsignedMultiplyHigh(aa1, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;
        d2 = aa1 * bb2;
        dd2 = Math.unsignedMultiplyHigh(aa1, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;
        d3 = aa1 * bb3;
        dd3 = Math.unsignedMultiplyHigh(aa1, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;
        d4 = aa1 * bb4;
        dd4 = Math.unsignedMultiplyHigh(aa1, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        n0 = n * modulus[0];
        dd0 += Math.unsignedMultiplyHigh(n, modulus[0]) << shift1 | (n0 >>> shift2);
        d0 += n0 & LIMB_MASK;
        n1 = n * modulus[1];
        dd1 += Math.unsignedMultiplyHigh(n, modulus[1]) << shift1 | (n1 >>> shift2);
        d1 += n1 & LIMB_MASK;
        n2 = n * modulus[2];
        dd2 += Math.unsignedMultiplyHigh(n, modulus[2]) << shift1 | (n2 >>> shift2);
        d2 += n2 & LIMB_MASK;
        n3 = n * modulus[3];
        dd3 += Math.unsignedMultiplyHigh(n, modulus[3]) << shift1 | (n3 >>> shift2);
        d3 += n3 & LIMB_MASK;
        n4 = n * modulus[4];
        dd4 += Math.unsignedMultiplyHigh(n, modulus[4]) << shift1 | (n4 >>> shift2);
        d4 += n4 & LIMB_MASK;

        c2 += d1 + dd0 + (d0 >>> BITS_PER_LIMB);
        c3 += d2 + dd1;
        c4 += d3 + dd2;
        c5 += d4 + dd3;
        c6 = dd4;

        // Row 2 - multiply by aa2 and reduce out c2
        d0 = aa2 * bb0;
        dd0 = Math.unsignedMultiplyHigh(aa2, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;
        d0 += c2;
        n = d0 & LIMB_MASK;
        d1 = aa2 * bb1;
        dd1 = Math.unsignedMultiplyHigh(aa2, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;
        d2 = aa2 * bb2;
        dd2 = Math.unsignedMultiplyHigh(aa2, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;
        d3 = aa2 * bb3;
        dd3 = Math.unsignedMultiplyHigh(aa2, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;
        d4 = aa2 * bb4;
        dd4 = Math.unsignedMultiplyHigh(aa2, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        n0 = n * modulus[0];
        dd0 += Math.unsignedMultiplyHigh(n, modulus[0]) << shift1 | (n0 >>> shift2);
        d0 += n0 & LIMB_MASK;
        n1 = n * modulus[1];
        dd1 += Math.unsignedMultiplyHigh(n, modulus[1]) << shift1 | (n1 >>> shift2);
        d1 += n1 & LIMB_MASK;
        n2 = n * modulus[2];
        dd2 += Math.unsignedMultiplyHigh(n, modulus[2]) << shift1 | (n2 >>> shift2);
        d2 += n2 & LIMB_MASK;
        n3 = n * modulus[3];
        dd3 += Math.unsignedMultiplyHigh(n, modulus[3]) << shift1 | (n3 >>> shift2);
        d3 += n3 & LIMB_MASK;
        n4 = n * modulus[4];
        dd4 += Math.unsignedMultiplyHigh(n, modulus[4]) << shift1 | (n4 >>> shift2);
        d4 += n4 & LIMB_MASK;

        c3 += d1 + dd0 + (d0 >>> BITS_PER_LIMB);
        c4 += d2 + dd1;
        c5 += d3 + dd2;
        c6 += d4 + dd3;
        c7 = dd4;

        // Row 3 - multiply by aa3 and reduce out c3
        d0 = aa3 * bb0;
        dd0 = Math.unsignedMultiplyHigh(aa3, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;
        d0 += c3;
        n = d0 & LIMB_MASK;
        d1 = aa3 * bb1;
        dd1 = Math.unsignedMultiplyHigh(aa3, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;
        d2 = aa3 * bb2;
        dd2 = Math.unsignedMultiplyHigh(aa3, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;
        d3 = aa3 * bb3;
        dd3 = Math.unsignedMultiplyHigh(aa3, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;
        d4 = aa3 * bb4;
        dd4 = Math.unsignedMultiplyHigh(aa3, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        n0 = n * modulus[0];
        dd0 += Math.unsignedMultiplyHigh(n, modulus[0]) << shift1 | (n0 >>> shift2);
        d0 += n0 & LIMB_MASK;
        n1 = n * modulus[1];
        dd1 += Math.unsignedMultiplyHigh(n, modulus[1]) << shift1 | (n1 >>> shift2);
        d1 += n1 & LIMB_MASK;
        n2 = n * modulus[2];
        dd2 += Math.unsignedMultiplyHigh(n, modulus[2]) << shift1 | (n2 >>> shift2);
        d2 += n2 & LIMB_MASK;
        n3 = n * modulus[3];
        dd3 += Math.unsignedMultiplyHigh(n, modulus[3]) << shift1 | (n3 >>> shift2);
        d3 += n3 & LIMB_MASK;
        n4 = n * modulus[4];
        dd4 += Math.unsignedMultiplyHigh(n, modulus[4]) << shift1 | (n4 >>> shift2);
        d4 += n4 & LIMB_MASK;

        c4 += d1 + dd0 + (d0 >>> BITS_PER_LIMB);
        c5 += d2 + dd1;
        c6 += d3 + dd2;
        c7 += d4 + dd3;
        c8 = dd4;

        // Row 4 - multiply by aa3 and reduce out c4
        d0 = aa4 * bb0;
        dd0 = Math.unsignedMultiplyHigh(aa4, bb0) << shift1 | (d0 >>> shift2);
        d0 &= LIMB_MASK;
        d0 += c4;
        n = d0 & LIMB_MASK;
        d1 = aa4 * bb1;
        dd1 = Math.unsignedMultiplyHigh(aa4, bb1) << shift1 | (d1 >>> shift2);
        d1 &= LIMB_MASK;
        d2 = aa4 * bb2;
        dd2 = Math.unsignedMultiplyHigh(aa4, bb2) << shift1 | (d2 >>> shift2);
        d2 &= LIMB_MASK;
        d3 = aa4 * bb3;
        dd3 = Math.unsignedMultiplyHigh(aa4, bb3) << shift1 | (d3 >>> shift2);
        d3 &= LIMB_MASK;
        d4 = aa4 * bb4;
        dd4 = Math.unsignedMultiplyHigh(aa4, bb4) << shift1 | (d4 >>> shift2);
        d4 &= LIMB_MASK;

        n0 = n * modulus[0];
        dd0 += Math.unsignedMultiplyHigh(n, modulus[0]) << shift1 | (n0 >>> shift2);
        d0 += n0 & LIMB_MASK;
        n1 = n * modulus[1];
        dd1 += Math.unsignedMultiplyHigh(n, modulus[1]) << shift1 | (n1 >>> shift2);
        d1 += n1 & LIMB_MASK;
        n2 = n * modulus[2];
        dd2 += Math.unsignedMultiplyHigh(n, modulus[2]) << shift1 | (n2 >>> shift2);
        d2 += n2 & LIMB_MASK;
        n3 = n * modulus[3];
        dd3 += Math.unsignedMultiplyHigh(n, modulus[3]) << shift1 | (n3 >>> shift2);
        d3 += n3 & LIMB_MASK;
        n4 = n * modulus[4];
        dd4 += Math.unsignedMultiplyHigh(n, modulus[4]) << shift1 | (n4 >>> shift2);
        d4 += n4 & LIMB_MASK;

        // Final carry propagate
        c5 += d1 + dd0 + (d0 >>> BITS_PER_LIMB);
        c6 += d2 + dd1 + (c5 >>> BITS_PER_LIMB);
        c7 += d3 + dd2 + (c6 >>> BITS_PER_LIMB);
        c8 += d4 + dd3 + (c7 >>> BITS_PER_LIMB);
        c9 = dd4 + (c8 >>> BITS_PER_LIMB);

        c5 &= LIMB_MASK;
        c6 &= LIMB_MASK;
        c7 &= LIMB_MASK;
        c8 &= LIMB_MASK;

        // At this point, the result {c5, c6, c7, c8, c9} could overflow by
        // one modulus. Subtract one modulus (with carry propagation), into
        // {c0, c1, c2, c3, c4}. Note that in this calculation, limbs are
        // signed
        c0 = c5 - modulus[0];
        c1 = c6 - modulus[1] + (c0 >> BITS_PER_LIMB);
        c0 &= LIMB_MASK;
        c2 = c7 - modulus[2] + (c1 >> BITS_PER_LIMB);
        c1 &= LIMB_MASK;
        c3 = c8 - modulus[3] + (c2 >> BITS_PER_LIMB);
        c2 &= LIMB_MASK;
        c4 = c9 - modulus[4] + (c3 >> BITS_PER_LIMB);
        c3 &= LIMB_MASK;

        // We now must select a result that is in range of [0,modulus). i.e.
        // either {c0-4} or {c5-9}. Iff {c0-4} is negative, then {c5-9} contains
        // the result. (After carry propagation) IF c4 is negative, {c0-4} is
        // negative. Arithmetic shift by 64 bits generates a mask from c4 that
        // can be used to select 'constant time' either {c0-4} or {c5-9}.
        long mask = c4 >> 63;
        r[0] = ((c5 & mask) | (c0 & ~mask));
        r[1] = ((c6 & mask) | (c1 & ~mask));
        r[2] = ((c7 & mask) | (c2 & ~mask));
        r[3] = ((c8 & mask) | (c3 & ~mask));
        r[4] = ((c9 & mask) | (c4 & ~mask));
    }

    @Override
    protected void finalCarryReduceLast(long[] limbs) {
        reduce(limbs);
    }

    @Override
    protected long carryValue(long x) {
        return x >> BITS_PER_LIMB;
    }

    @Override
    protected void postEncodeCarry(long[] v) {
        // not needed because carry is unsigned
    }

    // Proof:
    // carry * 2^256 (mod p) ==  carry * [2^256 - p] (mod p)
    //                       ==  carry * [2^256 - (2^256 -2^224 +2^192 +2^96 -1)] (mod p)
    //                       ==  carry * [2^224 -2^192 -2^96 +1] (mod p)
    @Override
    protected void reduce(long[] limbs) {
        long b0 = limbs[0];
        long b1 = limbs[1];
        long b2 = limbs[2];
        long b3 = limbs[3];
        long b4 = limbs[4];
        long carry = b4 >> 48; // max 16-bits
        b4 -= carry << 48;

        // 2^0 position
        b0 += carry;
        // -2^96
        b1 -= carry << 44;
        // -2^192
        b3 -= carry << 36;
        // 2^224
        b4 += carry << 16;

        b1 += b0 >> BITS_PER_LIMB;
        b2 += b1 >> BITS_PER_LIMB;
        b3 += b2 >> BITS_PER_LIMB;
        b4 += b3 >> BITS_PER_LIMB;

        b0 &= LIMB_MASK;
        b1 &= LIMB_MASK;
        b2 &= LIMB_MASK;
        b3 &= LIMB_MASK;

        long c0, c1, c2, c3, c4;
        c0 = modulus[0] + b0;
        c1 = modulus[1] + b1 + (c0 >> BITS_PER_LIMB);
        c0 &= LIMB_MASK;
        c2 = modulus[2] + b2 + (c1 >> BITS_PER_LIMB);
        c1 &= LIMB_MASK;
        c3 = modulus[3] + b3 + (c2 >> BITS_PER_LIMB);
        c2 &= LIMB_MASK;
        c4 = modulus[4] + b4 + (c3 >> BITS_PER_LIMB);
        c3 &= LIMB_MASK;

        long mask = b4 >> BITS_PER_LIMB; // Signed shift!

        limbs[0] = (b0 & ~mask) | (c0 & mask);
        limbs[1] = (b1 & ~mask) | (c1 & mask);
        limbs[2] = (b2 & ~mask) | (c2 & mask);
        limbs[3] = (b3 & ~mask) | (c3 & mask);
        limbs[4] = (b4 & ~mask) | (c4 & mask);
    }

    public ImmutableElement getElement(byte[] v, int offset, int length,
            byte highByte) {

        long[] vLimbs = new long[NUM_LIMBS];
        long[] montLimbs = new long[NUM_LIMBS];
        super.encode(v, offset, length, highByte, vLimbs);

        // Convert to Montgomery domain
        mult(vLimbs, h, montLimbs);
        return new ImmutableElement(montLimbs, 0);
    }

    /*
     * This function 'moves/reduces' digit 'v' to the 'lower' limbs
     *
     * The result is not reduced further. Carry propagation is not performed
     * (see IntegerPolynomial.reduceHigh() for how this method is used)
     *
     * Proof:
     *   v * 2^(i*52) (mod p) ==  v * 2^(52i) - v * 2^(52i-256) * p                               (mod p)
     *                        ==  v * 2^(52i) - v * 2^(52i-256) * (2^256 -2^224 +2^192 +2^96 -1)  (mod p)
     *                        ==  v * 2^(52i) - v * [2^(52i-256+256) -2^(52i-256+224) +2^(52i-256+192) +2^(52i-256+96) -2^(52i-256)] (mod p)
     *                        ==  v * 2^(52i) - v * [2^(52i) -2^(52i-32) +2^(52i-64) +2^(52i-160) -2^(52i-256)]                      (mod p)
     *
     *                        ==  v * [2^(52i-32) +2^(52i-52-12) +2^(52i-3*52-4) -2^(52i-4*52-48)] (mod p)
     */
    @Override
    protected void reduceIn(long[] limbs, long v, int i) {
        // Since top term (2^(52i-32)) will leave top 20 bits back in the same
        // position i,
        // "repeat same reduction on top 20 bits"
        v += v >> 32;

        // 2^(52i-32)
        limbs[i - 1] += (v << 20) & LIMB_MASK;

        // 2^(52i-52-12)
        limbs[i - 2] -= (v << 40) & LIMB_MASK;
        limbs[i - 1] -= v >> 12;

        // 2^(52i-3*52-4)
        limbs[i - 4] -= (v << 48) & LIMB_MASK;
        limbs[i - 3] -= v >> 4;

        // 2^(52i-4*52-48)
        limbs[i - 5] += (v << 4) & LIMB_MASK;
        limbs[i - 4] += v >> 48;
    }
}
