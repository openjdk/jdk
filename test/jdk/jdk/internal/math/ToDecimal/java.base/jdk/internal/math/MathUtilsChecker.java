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

import java.math.BigInteger;

import static java.lang.Double.*;
import static java.lang.Long.numberOfTrailingZeros;
import static java.lang.StrictMath.scalb;
import static java.math.BigInteger.*;
import static jdk.internal.math.MathUtils.*;

public class MathUtilsChecker extends BasicChecker {

    private static final BigInteger THREE = valueOf(3);

    // binary constants
    private static final int P =
            numberOfTrailingZeros(doubleToRawLongBits(3)) + 2;
    private static final int W = (SIZE - 1) - (P - 1);
    private static final int Q_MIN = (-1 << W - 1) - P + 3;
    private static final int Q_MAX = (1 << W - 1) - P;
    private static final long C_MIN = 1L << P - 1;
    private static final long C_MAX = (1L << P) - 1;

    // decimal constants
    private static final int K_MIN = flog10pow2(Q_MIN);
    private static final int K_MAX = flog10pow2(Q_MAX);
    private static final int H = flog10pow2(P) + 2;

    private static String gReason(int k) {
        return "g(" + k + ") is incorrect";
    }

    /*
    Let
        10^(-k) = beta 2^r
    for the unique integer r and real beta meeting
        2^125 <= beta < 2^126
    Further, let g1 = g1(k), g0 = g0(k) and g = g1 2^63 + g0,
    where g1 and g0 are as in MathUtils
    Check that:
        2^62 <= g1 < 2^63,
        0 <= g0 < 2^63,
        g - 1 <= beta < g,    (that is, g = floor(beta) + 1)
    The last predicate, after multiplying by 2^r, is equivalent to
        (g - 1) 2^r <= 10^(-k) < g 2^r
    This is the predicate that will be checked in various forms.
     */
    private static void testG(int k) {
        long g1 = g1(k);
        long g0 = g0(k);
        // 2^62 <= g1 < 2^63, 0 <= g0 < 2^63
        addOnFail((g1 >>> (Long.SIZE - 2)) == 0b01 && g0 >= 0, gReason(k));

        BigInteger g = valueOf(g1).shiftLeft(63).or(valueOf(g0));
        // double check that 2^125 <= g < 2^126
        addOnFail(g.signum() > 0 && g.bitLength() == 126, gReason(k));

        // see javadoc of MathUtils.g1(int)
        int r = flog2pow10(-k) - 125;

        /*
        The predicate
            (g - 1) 2^r <= 10^(-k) < g 2^r
        is equivalent to
            g - 1 <= 10^(-k) 2^(-r) < g
        When
            k <= 0 & r < 0
        all numerical subexpressions are integer-valued. This is the same as
            g - 1 = 10^(-k) 2^(-r)
         */
        if (k <= 0 && r < 0) {
            addOnFail(
                    g.subtract(ONE).compareTo(TEN.pow(-k).shiftLeft(-r)) == 0,
                    gReason(k));
            return;
        }

        /*
        The predicate
            (g - 1) 2^r <= 10^(-k) < g 2^r
        is equivalent to
            g 10^k - 10^k <= 2^(-r) < g 10^k
        When
            k > 0 & r < 0
        all numerical subexpressions are integer-valued.
         */
        if (k > 0 && r < 0) {
            BigInteger pow5 = TEN.pow(k);
            BigInteger mhs = ONE.shiftLeft(-r);
            BigInteger rhs = g.multiply(pow5);
            addOnFail(rhs.subtract(pow5).compareTo(mhs) <= 0
                            && mhs.compareTo(rhs) < 0,
                    gReason(k));
            return;
        }

        /*
        Finally, when
            k <= 0 & r >= 0
        the predicate
            (g - 1) 2^r <= 10^(-k) < g 2^r
        can be used straightforwardly as all numerical subexpressions are
        already integer-valued.
         */
        if (k <= 0) {
            BigInteger mhs = TEN.pow(-k);
            addOnFail(g.subtract(ONE).shiftLeft(r).compareTo(mhs) <= 0 &&
                            mhs.compareTo(g.shiftLeft(r)) < 0,
                    gReason(k));
            return;
        }

        /*
        For combinatorial reasons, the only remaining case is
            k > 0 & r >= 0
        which, however, cannot arise. Indeed, the predicate
            (g - 1) 2^r <= 10^(-k) < g 2^r
        has a positive integer left-hand side and a middle side < 1,
        which cannot hold.
         */
        addOnFail(false, "unexpected case for g(" + k + ")");
    }

    /*
    Verifies the soundness of the values returned by g1() and g0().
     */
    private static void testG() {
        for (int k = MathUtils.K_MIN; k <= MathUtils.K_MAX; ++k) {
            testG(k);
        }
    }

    private static String flog10threeQuartersPow2Reason(int e) {
        return "flog10threeQuartersPow2(" + e + ") is incorrect";
    }

    /*
    Let
        k = floor(log10(3/4 2^e))
    The method verifies that
        k = flog10threeQuartersPow2(e),    Q_MIN <= e <= Q_MAX
    This range covers all binary exponents of doubles and floats.

    The first equation above is equivalent to
        10^k <= 3 2^(e-2) < 10^(k+1)
    Equality never holds. Henceforth, the predicate to check is
        10^k < 3 2^(e-2) < 10^(k+1)
    This will be transformed in various ways for checking purposes.

    For integer n > 0, let further
        b = len2(n)
    denote its length in bits. This means exactly the same as
        2^(b-1) <= n < 2^b
     */
    private static void testFlog10threeQuartersPow2() {
        // First check the case e = 1
        addOnFail(flog10threeQuartersPow2(1) == 0,
                flog10threeQuartersPow2Reason(1));

        /*
        Now check the range Q_MIN <= e <= 0.
        By rewriting, the predicate to check is equivalent to
            3 10^(-k-1) < 2^(2-e) < 3 10^(-k)
        As e <= 0, it follows that 2^(2-e) >= 4 and the right inequality
        implies k < 0, so the powers of 10 are integers.

        The left inequality is equivalent to
            len2(3 10^(-k-1)) <= 2 - e
        and the right inequality to
            2 - e < len2(3 10^(-k))
        The original predicate is therefore equivalent to
            len2(3 10^(-k-1)) <= 2 - e < len2(3 10^(-k))

        Starting with e = 0 and decrementing until the lower bound, the code
        keeps track of the two powers of 10 to avoid recomputing them.
        This is easy because at each iteration k changes at most by 1. A simple
        multiplication by 10 computes the next power of 10 when needed.
         */
        int e = 0;
        int k0 = flog10threeQuartersPow2(e);
        addOnFail(k0 < 0, flog10threeQuartersPow2Reason(e));
        BigInteger l = THREE.multiply(TEN.pow(-k0 - 1));
        BigInteger u = l.multiply(TEN);
        for (;;) {
            addOnFail(l.bitLength() <= 2 - e & 2 - e < u.bitLength(),
                    flog10threeQuartersPow2Reason(e));
            --e;
            if (e < Q_MIN) {
                break;
            }
            int kp = flog10threeQuartersPow2(e);
            addOnFail(kp <= k0, flog10threeQuartersPow2Reason(e));
            if (kp < k0) {
                // k changes at most by 1 at each iteration, hence:
                addOnFail(k0 - kp == 1, flog10threeQuartersPow2Reason(e));
                k0 = kp;
                l = u;
                u = u.multiply(TEN);
            }
        }

        /*
        Finally, check the range 2 <= e <= Q_MAX.
        In predicate
            10^k < 3 2^(e-2) < 10^(k+1)
        the right inequality shows that k >= 0 as soon as e >= 2.
        It is equivalent to
            10^k / 3 < 2^(e-2) < 10^(k+1) / 3
        Both the powers of 10 and the powers of 2 are integers.
        The left inequality is therefore equivalent to
            floor(10^k / 3) < 2^(e-2)
        and thus to
            len2(floor(10^k / 3)) <= e - 2
        while the right inequality is equivalent to
            2^(e-2) <= floor(10^(k+1) / 3)
        and hence to
            e - 2 < len2(floor(10^(k+1) / 3))
        These are summarized as
            len2(floor(10^k / 3)) <= e - 2 < len2(floor(10^(k+1) / 3))
         */
        e = 2;
        k0 = flog10threeQuartersPow2(e);
        addOnFail(k0 >= 0, flog10threeQuartersPow2Reason(e));
        BigInteger l10 = TEN.pow(k0);
        BigInteger u10 = l10.multiply(TEN);
        l = l10.divide(THREE);
        u = u10.divide(THREE);
        for (;;) {
            addOnFail(l.bitLength() <= e - 2 & e - 2 < u.bitLength(),
                    flog10threeQuartersPow2Reason(e));
            ++e;
            if (e > Q_MAX) {
                break;
            }
            int kp = flog10threeQuartersPow2(e);
            addOnFail(kp >= k0, flog10threeQuartersPow2Reason(e));
            if (kp > k0) {
                // k changes at most by 1 at each iteration, hence:
                addOnFail(kp - k0 == 1, flog10threeQuartersPow2Reason(e));
                k0 = kp;
                u10 = u10.multiply(TEN);
                l = u;
                u = u10.divide(THREE);
            }
        }
    }

    private static String flog10pow2Reason(int e) {
        return "flog10pow2(" + e + ") is incorrect";
    }

    /*
    Let
        k = floor(log10(2^e))
    The method verifies that
        k = flog10pow2(e),    Q_MIN <= e <= Q_MAX
    This range covers all binary exponents of doubles and floats.

    The first equation above is equivalent to
        10^k <= 2^e < 10^(k+1)
    Equality holds iff e = k = 0.
    Henceforth, the predicates to check are equivalent to
        k = 0,    if e = 0
        10^k < 2^e < 10^(k+1),    otherwise
    The latter will be transformed in various ways for checking purposes.

    For integer n > 0, let further
        b = len2(n)
    denote its length in bits. This means exactly the same as
        2^(b-1) <= n < 2^b
     */
    private static void testFlog10pow2() {
        // First check the case e = 0
        addOnFail(flog10pow2(0) == 0, flog10pow2Reason(0));

        /*
        Now check the range F * Q_MIN <= e < 0.
        By inverting all quantities, the predicate to check is equivalent to
            10^(-k-1) < 2^(-e) < 10^(-k)
        As e < 0, it follows that 2^(-e) >= 2 and the right inequality
        implies k < 0.
        The left inequality means exactly the same as
            len2(10^(-k-1)) <= -e
        Similarly, the right inequality is equivalent to
            -e < len2(10^(-k))
        The original predicate is therefore equivalent to
            len2(10^(-k-1)) <= -e < len2(10^(-k))
        The powers of 10 are integers because k < 0.

        Starting with e = -1 and decrementing towards the lower bound, the code
        keeps track of the two powers of 10 to avoid recomputing them.
        This is easy because at each iteration k changes at most by 1. A simple
        multiplication by 10 computes the next power of 10 when needed.
         */
        int e = -1;
        int k = flog10pow2(e);
        addOnFail(k < 0, flog10pow2Reason(e));
        BigInteger l = TEN.pow(-k - 1);
        BigInteger u = l.multiply(TEN);
        for (;;) {
            addOnFail(l.bitLength() <= -e & -e < u.bitLength(),
                    flog10pow2Reason(e));
            --e;
            if (e < Q_MIN) {
                break;
            }
            int kp = flog10pow2(e);
            addOnFail(kp <= k, flog10pow2Reason(e));
            if (kp < k) {
                // k changes at most by 1 at each iteration, hence:
                addOnFail(k - kp == 1, flog10pow2Reason(e));
                k = kp;
                l = u;
                u = u.multiply(TEN);
            }
        }

        /*
        Finally, in a similar vein, check the range 0 <= e <= Q_MAX.
        In predicate
            10^k < 2^e < 10^(k+1)
        the right inequality shows that k >= 0.
        The left inequality means the same as
            len2(10^k) <= e
        and the right inequality holds iff
            e < len2(10^(k+1))
        The original predicate is thus equivalent to
            len2(10^k) <= e < len2(10^(k+1))
        As k >= 0, the powers of 10 are integers.
         */
        e = 1;
        k = flog10pow2(e);
        addOnFail(k >= 0, flog10pow2Reason(e));
        l = TEN.pow(k);
        u = l.multiply(TEN);
        for (;;) {
            addOnFail(l.bitLength() <= e & e < u.bitLength(),
                    flog10pow2Reason(e));
            ++e;
            if (e > Q_MAX) {
                break;
            }
            int kp = flog10pow2(e);
            addOnFail(kp >= k, flog10pow2Reason(e));
            if (kp > k) {
                // k changes at most by 1 at each iteration, hence:
                addOnFail(kp - k == 1, flog10pow2Reason(e));
                k = kp;
                l = u;
                u = u.multiply(TEN);
            }
        }
    }

    private static String flog2pow10Reason(int e) {
        return "flog2pow10(" + e + ") is incorrect";
    }

    /*
    Let
        k = floor(log2(10^e))
    The method verifies that
        k = flog2pow10(e),    -K_MAX <= e <= -K_MIN
    This range covers all decimal exponents of doubles and floats.

    The first equation above is equivalent to
        2^k <= 10^e < 2^(k+1)
    Equality holds iff e = 0, implying k = 0.
    Henceforth, the equivalent predicates to check are
        k = 0,    if e = 0
        2^k < 10^e < 2^(k+1),    otherwise
    The latter will be transformed in various ways for checking purposes.

    For integer n > 0, let further
        b = len2(n)
    denote its length in bits. This means exactly the same as
        2^(b-1) <= n < 2^b
    */
    private static void testFlog2pow10() {
        // First check the case e = 0
        addOnFail(flog2pow10(0) == 0, flog2pow10Reason(0));

        /*
        Now check the range K_MIN <= e < 0.
        By inverting all quantities, the predicate to check is equivalent to
            2^(-k-1) < 10^(-e) < 2^(-k)
        As e < 0, this leads to 10^(-e) >= 10 and the right inequality implies
        k <= -4.
        The above means the same as
            len2(10^(-e)) = -k
        The powers of 10 are integer values since e < 0.
         */
        int e = -1;
        int k0 = flog2pow10(e);
        addOnFail(k0 <= -4, flog2pow10Reason(e));
        BigInteger l = TEN;
        for (;;) {
            addOnFail(l.bitLength() == -k0, flog2pow10Reason(e));
            --e;
            if (e < -K_MAX) {
                break;
            }
            k0 = flog2pow10(e);
            l = l.multiply(TEN);
        }

        /*
        Finally, check the range 0 < e <= K_MAX.
        From the predicate
            2^k < 10^e < 2^(k+1)
        as e > 0, it follows that 10^e >= 10 and the right inequality implies
        k >= 3.
        The above means the same as
            len2(10^e) = k + 1
        The powers of 10 are all integer valued, as e > 0.
         */
        e = 1;
        k0 = flog2pow10(e);
        addOnFail(k0 >= 3, flog2pow10Reason(e));
        l = TEN;
        for (;;) {
            addOnFail(l.bitLength() == k0 + 1, flog2pow10Reason(e));
            ++e;
            if (e > -K_MIN) {
                break;
            }
            k0 = flog2pow10(e);
            l = l.multiply(TEN);
        }
    }

    private static void testBinaryConstants() {
        addOnFail((long) (double) C_MIN == C_MIN, "C_MIN");
        addOnFail((long) (double) C_MAX == C_MAX, "C_MAX");
        addOnFail(scalb(1.0, Q_MIN) == MIN_VALUE, "MIN_VALUE");
        addOnFail(scalb((double) C_MIN, Q_MIN) == MIN_NORMAL, "MIN_NORMAL");
        addOnFail(scalb((double) C_MAX, Q_MAX) == MAX_VALUE, "MAX_VALUE");
    }

    private static void testDecimalConstants() {
        addOnFail(K_MIN == MathUtils.K_MIN, "K_MIN");
        addOnFail(K_MAX == MathUtils.K_MAX, "K_MAX");
        addOnFail(H == MathUtils.H, "H");
    }

    private static String pow10Reason(int e) {
        return "pow10(" + e + ") is incorrect";
    }

    private static void testPow10() {
        int e = 0;
        long pow = 1;
        for (; e <= H; e += 1, pow *= 10) {
            addOnFail(pow == pow10(e), pow10Reason(e));
        }
    }

    public static void test() {
        testBinaryConstants();
        testFlog10pow2();
        testFlog10threeQuartersPow2();
        testDecimalConstants();
        testFlog2pow10();
        testPow10();
        testG();
        throwOnErrors("MathUtilsChecker");
    }

}
