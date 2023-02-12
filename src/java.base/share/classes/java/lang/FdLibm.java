/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

/**
 * Port of the "Freely Distributable Math Library", version 5.3, from
 * C to Java.
 *
 * <p>The C version of fdlibm relied on the idiom of pointer aliasing
 * a 64-bit double floating-point value as a two-element array of
 * 32-bit integers and reading and writing the two halves of the
 * double independently. This coding pattern was problematic to C
 * optimizers and not directly expressible in Java. Therefore, rather
 * than a memory level overlay, if portions of a double need to be
 * operated on as integer values, the standard library methods for
 * bitwise floating-point to integer conversion,
 * Double.longBitsToDouble and Double.doubleToRawLongBits, are directly
 * or indirectly used.
 *
 * <p>The C version of fdlibm also took some pains to signal the
 * correct IEEE 754 exceptional conditions divide by zero, invalid,
 * overflow and underflow. For example, overflow would be signaled by
 * {@code huge * huge} where {@code huge} was a large constant that
 * would overflow when squared. Since IEEE floating-point exceptional
 * handling is not supported natively in the JVM, such coding patterns
 * have been omitted from this port. For example, rather than {@code
 * return huge * huge}, this port will use {@code return INFINITY}.
 *
 * <p>Various comparison and arithmetic operations in fdlibm could be
 * done either based on the integer view of a value or directly on the
 * floating-point representation. Which idiom is faster may depend on
 * platform specific factors. However, for code clarity if no other
 * reason, this port will favor expressing the semantics of those
 * operations in terms of floating-point operations when convenient to
 * do so.
 */
class FdLibm {
    // Constants used by multiple algorithms
    private static final double INFINITY = Double.POSITIVE_INFINITY;
    private static final double TWO54   =  0x1.0p54; // 1.80143985094819840000e+16

    private FdLibm() {
        throw new UnsupportedOperationException("No FdLibm instances for you.");
    }

    /**
     * Return the low-order 32 bits of the double argument as an int.
     */
    private static int __LO(double x) {
        long transducer = Double.doubleToRawLongBits(x);
        return (int)transducer;
    }

    /**
     * Return a double with its low-order bits of the second argument
     * and the high-order bits of the first argument..
     */
    private static double __LO(double x, int low) {
        long transX = Double.doubleToRawLongBits(x);
        return Double.longBitsToDouble((transX & 0xFFFF_FFFF_0000_0000L) |
                                       (low    & 0x0000_0000_FFFF_FFFFL));
    }

    /**
     * Return the high-order 32 bits of the double argument as an int.
     */
    private static int __HI(double x) {
        long transducer = Double.doubleToRawLongBits(x);
        return (int)(transducer >> 32);
    }

    /**
     * Return a double with its high-order bits of the second argument
     * and the low-order bits of the first argument..
     */
    private static double __HI(double x, int high) {
        long transX = Double.doubleToRawLongBits(x);
        return Double.longBitsToDouble((transX & 0x0000_0000_FFFF_FFFFL) |
                                       ( ((long)high)) << 32 );
    }

    /**
     * cbrt(x)
     * Return cube root of x
     */
    public static class Cbrt {
        // unsigned
        private static final int B1 = 715094163; /* B1 = (682-0.03306235651)*2**20 */
        private static final int B2 = 696219795; /* B2 = (664-0.03306235651)*2**20 */

        private static final double C =  0x1.15f15f15f15f1p-1; //   19/35   ~= 5.42857142857142815906e-01
        private static final double D = -0x1.691de2532c834p-1; // -864/1225 ~= 7.05306122448979611050e-01
        private static final double E =  0x1.6a0ea0ea0ea0fp0;  //   99/70   ~= 1.41428571428571436819e+00
        private static final double F =  0x1.9b6db6db6db6ep0;  //   45/28   ~= 1.60714285714285720630e+00
        private static final double G =  0x1.6db6db6db6db7p-2; //    5/14   ~= 3.57142857142857150787e-01

        private Cbrt() {
            throw new UnsupportedOperationException();
        }

        public static double compute(double x) {
            double  t = 0.0;
            double sign;

            if (x == 0.0 || !Double.isFinite(x))
                return x; // Handles signed zeros properly

            sign = (x < 0.0) ? -1.0:  1.0;

            x = Math.abs(x);   // x <- |x|

            // Rough cbrt to 5 bits
            if (x < 0x1.0p-1022) {     // subnormal number
                t = 0x1.0p54;          // set t= 2**54
                t *= x;
                t = __HI(t, __HI(t)/3 + B2);
            } else {
                int hx = __HI(x);           // high word of x
                t = __HI(t, hx/3 + B1);
            }

            // New cbrt to 23 bits, may be implemented in single precision
            double  r, s, w;
            r = t * t/x;
            s = C + r*t;
            t *= G + F/(s + E + D/s);

            // Chopped to 20 bits and make it larger than cbrt(x)
            t = __LO(t, 0);
            t = __HI(t, __HI(t) + 0x00000001);

            // One step newton iteration to 53 bits with error less than 0.667 ulps
            s = t * t;          // t*t is exact
            r = x / s;
            w = t + t;
            r = (r - t)/(w + r);  // r-s is exact
            t = t + t*r;

            // Restore the original sign bit
            return sign * t;
        }
    }

    /**
     * hypot(x,y)
     *
     * Method :
     *      If (assume round-to-nearest) z = x*x + y*y
     *      has error less than sqrt(2)/2 ulp, than
     *      sqrt(z) has error less than 1 ulp (exercise).
     *
     *      So, compute sqrt(x*x + y*y) with some care as
     *      follows to get the error below 1 ulp:
     *
     *      Assume x > y > 0;
     *      (if possible, set rounding to round-to-nearest)
     *      1. if x > 2y  use
     *              x1*x1 + (y*y + (x2*(x + x1))) for x*x + y*y
     *      where x1 = x with lower 32 bits cleared, x2 = x - x1; else
     *      2. if x <= 2y use
     *              t1*y1 + ((x-y) * (x-y) + (t1*y2 + t2*y))
     *      where t1 = 2x with lower 32 bits cleared, t2 = 2x - t1,
     *      y1= y with lower 32 bits chopped, y2 = y - y1.
     *
     *      NOTE: scaling may be necessary if some argument is too
     *            large or too tiny
     *
     * Special cases:
     *      hypot(x,y) is INF if x or y is +INF or -INF; else
     *      hypot(x,y) is NAN if x or y is NAN.
     *
     * Accuracy:
     *      hypot(x,y) returns sqrt(x^2 + y^2) with error less
     *      than 1 ulp (unit in the last place)
     */
    public static class Hypot {
        public static final double TWO_MINUS_600 = 0x1.0p-600;
        public static final double TWO_PLUS_600  = 0x1.0p+600;

        private Hypot() {
            throw new UnsupportedOperationException();
        }

        public static double compute(double x, double y) {
            double a = Math.abs(x);
            double b = Math.abs(y);

            if (!Double.isFinite(a) || !Double.isFinite(b)) {
                if (a == INFINITY || b == INFINITY)
                    return INFINITY;
                else
                    return a + b; // Propagate NaN significand bits
            }

            if (b > a) {
                double tmp = a;
                a = b;
                b = tmp;
            }
            assert a >= b;

            // Doing bitwise conversion after screening for NaN allows
            // the code to not worry about the possibility of
            // "negative" NaN values.

            // Note: the ha and hb variables are the high-order
            // 32-bits of a and b stored as integer values. The ha and
            // hb values are used first for a rough magnitude
            // comparison of a and b and second for simulating higher
            // precision by allowing a and b, respectively, to be
            // decomposed into non-overlapping portions. Both of these
            // uses could be eliminated. The magnitude comparison
            // could be eliminated by extracting and comparing the
            // exponents of a and b or just be performing a
            // floating-point divide.  Splitting a floating-point
            // number into non-overlapping portions can be
            // accomplished by judicious use of multiplies and
            // additions. For details see T. J. Dekker, A Floating-Point
            // Technique for Extending the Available Precision,
            // Numerische Mathematik, vol. 18, 1971, pp.224-242 and
            // subsequent work.

            int ha = __HI(a);        // high word of a
            int hb = __HI(b);        // high word of b

            if ((ha - hb) > 0x3c00000) {
                return a + b;  // x / y > 2**60
            }

            int k = 0;
            if (a > 0x1.00000_ffff_ffffp500) {   // a > ~2**500
                // scale a and b by 2**-600
                ha -= 0x25800000;
                hb -= 0x25800000;
                a = a * TWO_MINUS_600;
                b = b * TWO_MINUS_600;
                k += 600;
            }
            double t1, t2;
            if (b < 0x1.0p-500) {   // b < 2**-500
                if (b < Double.MIN_NORMAL) {      // subnormal b or 0 */
                    if (b == 0.0)
                        return a;
                    t1 = 0x1.0p1022;   // t1 = 2^1022
                    b *= t1;
                    a *= t1;
                    k -= 1022;
                } else {            // scale a and b by 2^600
                    ha += 0x25800000;       // a *= 2^600
                    hb += 0x25800000;       // b *= 2^600
                    a = a * TWO_PLUS_600;
                    b = b * TWO_PLUS_600;
                    k -= 600;
                }
            }
            // medium size a and b
            double w = a - b;
            if (w > b) {
                t1 = 0;
                t1 = __HI(t1, ha);
                t2 = a - t1;
                w  = Math.sqrt(t1*t1 - (b*(-b) - t2 * (a + t1)));
            } else {
                double y1, y2;
                a  = a + a;
                y1 = 0;
                y1 = __HI(y1, hb);
                y2 = b - y1;
                t1 = 0;
                t1 = __HI(t1, ha + 0x00100000);
                t2 = a - t1;
                w  = Math.sqrt(t1*y1 - (w*(-w) - (t1*y2 + t2*b)));
            }
            if (k != 0) {
                return Math.powerOfTwoD(k) * w;
            } else
                return w;
        }
    }

    /**
     * Compute x**y
     *                    n
     * Method:  Let x =  2   * (1+f)
     *      1. Compute and return log2(x) in two pieces:
     *              log2(x) = w1 + w2,
     *         where w1 has 53 - 24 = 29 bit trailing zeros.
     *      2. Perform y*log2(x) = n+y' by simulating multi-precision
     *         arithmetic, where |y'| <= 0.5.
     *      3. Return x**y = 2**n*exp(y'*log2)
     *
     * Special cases:
     *      1.  (anything) ** 0  is 1
     *      2.  (anything) ** 1  is itself
     *      3.  (anything) ** NAN is NAN
     *      4.  NAN ** (anything except 0) is NAN
     *      5.  +-(|x| > 1) **  +INF is +INF
     *      6.  +-(|x| > 1) **  -INF is +0
     *      7.  +-(|x| < 1) **  +INF is +0
     *      8.  +-(|x| < 1) **  -INF is +INF
     *      9.  +-1         ** +-INF is NAN
     *      10. +0 ** (+anything except 0, NAN)               is +0
     *      11. -0 ** (+anything except 0, NAN, odd integer)  is +0
     *      12. +0 ** (-anything except 0, NAN)               is +INF
     *      13. -0 ** (-anything except 0, NAN, odd integer)  is +INF
     *      14. -0 ** (odd integer) = -( +0 ** (odd integer) )
     *      15. +INF ** (+anything except 0,NAN) is +INF
     *      16. +INF ** (-anything except 0,NAN) is +0
     *      17. -INF ** (anything)  = -0 ** (-anything)
     *      18. (-anything) ** (integer) is (-1)**(integer)*(+anything**integer)
     *      19. (-anything except 0 and inf) ** (non-integer) is NAN
     *
     * Accuracy:
     *      pow(x,y) returns x**y nearly rounded. In particular
     *                      pow(integer,integer)
     *      always returns the correct integer provided it is
     *      representable.
     */
    public static class Pow {
        private Pow() {
            throw new UnsupportedOperationException();
        }

        public static double compute(final double x, final double y) {
            double z;
            double r, s, t, u, v, w;
            int i, j, k, n;

            // y == zero: x**0 = 1
            if (y == 0.0)
                return 1.0;

            // +/-NaN return x + y to propagate NaN significands
            if (Double.isNaN(x) || Double.isNaN(y))
                return x + y;

            final double y_abs = Math.abs(y);
            double x_abs   = Math.abs(x);
            // Special values of y
            if (y == 2.0) {
                return x * x;
            } else if (y == 0.5) {
                if (x >= -Double.MAX_VALUE) // Handle x == -infinity later
                    return Math.sqrt(x + 0.0); // Add 0.0 to properly handle x == -0.0
            } else if (y_abs == 1.0) {        // y is  +/-1
                return (y == 1.0) ? x : 1.0 / x;
            } else if (y_abs == INFINITY) {       // y is +/-infinity
                if (x_abs == 1.0)
                    return  y - y;         // inf**+/-1 is NaN
                else if (x_abs > 1.0) // (|x| > 1)**+/-inf = inf, 0
                    return (y >= 0) ? y : 0.0;
                else                       // (|x| < 1)**-/+inf = inf, 0
                    return (y < 0) ? -y : 0.0;
            }

            final int hx = __HI(x);
            int ix = hx & 0x7fffffff;

            /*
             * When x < 0, determine if y is an odd integer:
             * y_is_int = 0       ... y is not an integer
             * y_is_int = 1       ... y is an odd int
             * y_is_int = 2       ... y is an even int
             */
            int y_is_int  = 0;
            if (hx < 0) {
                if (y_abs >= 0x1.0p53)   // |y| >= 2^53 = 9.007199254740992E15
                    y_is_int = 2; // y is an even integer since ulp(2^53) = 2.0
                else if (y_abs >= 1.0) { // |y| >= 1.0
                    long y_abs_as_long = (long) y_abs;
                    if ( ((double) y_abs_as_long) == y_abs) {
                        y_is_int = 2 -  (int)(y_abs_as_long & 0x1L);
                    }
                }
            }

            // Special value of x
            if (x_abs == 0.0 ||
                x_abs == INFINITY ||
                x_abs == 1.0) {
                z = x_abs;                 // x is +/-0, +/-inf, +/-1
                if (y < 0.0)
                    z = 1.0/z;     // z = (1/|x|)
                if (hx < 0) {
                    if (((ix - 0x3ff00000) | y_is_int) == 0) {
                        z = (z-z)/(z-z); // (-1)**non-int is NaN
                    } else if (y_is_int == 1)
                        z = -1.0 * z;             // (x < 0)**odd = -(|x|**odd)
                }
                return z;
            }

            n = (hx >> 31) + 1;

            // (x < 0)**(non-int) is NaN
            if ((n | y_is_int) == 0)
                return (x-x)/(x-x);

            s = 1.0; // s (sign of result -ve**odd) = -1 else = 1
            if ( (n | (y_is_int - 1)) == 0)
                s = -1.0; // (-ve)**(odd int)

            double p_h, p_l, t1, t2;
            // |y| is huge
            if (y_abs > 0x1.00000_ffff_ffffp31) { // if |y| > ~2**31
                final double INV_LN2   =  0x1.7154_7652_b82fep0;   //  1.44269504088896338700e+00 = 1/ln2
                final double INV_LN2_H =  0x1.715476p0;            //  1.44269502162933349609e+00 = 24 bits of 1/ln2
                final double INV_LN2_L =  0x1.4ae0_bf85_ddf44p-26; //  1.92596299112661746887e-08 = 1/ln2 tail

                // Over/underflow if x is not close to one
                if (x_abs < 0x1.fffff_0000_0000p-1) // |x| < ~0.9999995231628418
                    return (y < 0.0) ? s * INFINITY : s * 0.0;
                if (x_abs > 0x1.00000_ffff_ffffp0)         // |x| > ~1.0
                    return (y > 0.0) ? s * INFINITY : s * 0.0;
                /*
                 * now |1-x| is tiny <= 2**-20, sufficient to compute
                 * log(x) by x - x^2/2 + x^3/3 - x^4/4
                 */
                t = x_abs - 1.0;        // t has 20 trailing zeros
                w = (t * t) * (0.5 - t * (0.3333333333333333333333 - t * 0.25));
                u = INV_LN2_H * t;      // INV_LN2_H has 21 sig. bits
                v =  t * INV_LN2_L - w * INV_LN2;
                t1 = u + v;
                t1 =__LO(t1, 0);
                t2 = v - (t1 - u);
            } else {
                final double CP      =  0x1.ec70_9dc3_a03fdp-1;  //  9.61796693925975554329e-01 = 2/(3ln2)
                final double CP_H    =  0x1.ec709ep-1;           //  9.61796700954437255859e-01 = (float)cp
                final double CP_L    = -0x1.e2fe_0145_b01f5p-28; // -7.02846165095275826516e-09 = tail of CP_H

                double z_h, z_l, ss, s2, s_h, s_l, t_h, t_l;
                n = 0;
                // Take care of subnormal numbers
                if (ix < 0x00100000) {
                    x_abs *= 0x1.0p53; // 2^53 = 9007199254740992.0
                    n -= 53;
                    ix = __HI(x_abs);
                }
                n  += ((ix) >> 20) - 0x3ff;
                j  = ix & 0x000fffff;
                // Determine interval
                ix = j | 0x3ff00000;          // Normalize ix
                if (j <= 0x3988E)
                    k = 0;         // |x| <sqrt(3/2)
                else if (j < 0xBB67A)
                    k = 1;         // |x| <sqrt(3)
                else {
                    k = 0;
                    n += 1;
                    ix -= 0x00100000;
                }
                x_abs = __HI(x_abs, ix);

                // Compute ss = s_h + s_l = (x-1)/(x+1) or (x-1.5)/(x+1.5)

                final double BP[]    = {1.0,
                                       1.5};
                final double DP_H[]  = {0.0,
                                        0x1.2b80_34p-1};        // 5.84962487220764160156e-01
                final double DP_L[]  = {0.0,
                                        0x1.cfde_b43c_fd006p-27};// 1.35003920212974897128e-08

                // Poly coefs for (3/2)*(log(x)-2s-2/3*s**3
                final double L1      =  0x1.3333_3333_33303p-1;  //  5.99999999999994648725e-01
                final double L2      =  0x1.b6db_6db6_fabffp-2;  //  4.28571428578550184252e-01
                final double L3      =  0x1.5555_5518_f264dp-2;  //  3.33333329818377432918e-01
                final double L4      =  0x1.1746_0a91_d4101p-2;  //  2.72728123808534006489e-01
                final double L5      =  0x1.d864_a93c_9db65p-3;  //  2.30660745775561754067e-01
                final double L6      =  0x1.a7e2_84a4_54eefp-3;  //  2.06975017800338417784e-01
                u = x_abs - BP[k];               // BP[0]=1.0, BP[1]=1.5
                v = 1.0 / (x_abs + BP[k]);
                ss = u * v;
                s_h = ss;
                s_h = __LO(s_h, 0);
                // t_h=x_abs + BP[k] High
                t_h = 0.0;
                t_h = __HI(t_h, ((ix >> 1) | 0x20000000) + 0x00080000 + (k << 18) );
                t_l = x_abs - (t_h - BP[k]);
                s_l = v * ((u - s_h * t_h) - s_h * t_l);
                // Compute log(x_abs)
                s2 = ss * ss;
                r = s2 * s2* (L1 + s2 * (L2 + s2 * (L3 + s2 * (L4 + s2 * (L5 + s2 * L6)))));
                r += s_l * (s_h + ss);
                s2  = s_h * s_h;
                t_h = 3.0 + s2 + r;
                t_h = __LO(t_h, 0);
                t_l = r - ((t_h - 3.0) - s2);
                // u+v = ss*(1+...)
                u = s_h * t_h;
                v = s_l * t_h + t_l * ss;
                // 2/(3log2)*(ss + ...)
                p_h = u + v;
                p_h = __LO(p_h, 0);
                p_l = v - (p_h - u);
                z_h = CP_H * p_h;             // CP_H + CP_L = 2/(3*log2)
                z_l = CP_L * p_h + p_l * CP + DP_L[k];
                // log2(x_abs) = (ss + ..)*2/(3*log2) = n + DP_H + z_h + z_l
                t = (double)n;
                t1 = (((z_h + z_l) + DP_H[k]) + t);
                t1 = __LO(t1, 0);
                t2 = z_l - (((t1 - t) - DP_H[k]) - z_h);
            }

            // Split up y into (y1 + y2) and compute (y1 + y2) * (t1 + t2)
            double y1  = y;
            y1 = __LO(y1, 0);
            p_l = (y - y1) * t1 + y * t2;
            p_h = y1 * t1;
            z = p_l + p_h;
            j = __HI(z);
            i = __LO(z);
            if (j >= 0x40900000) {                           // z >= 1024
                if (((j - 0x40900000) | i)!=0)               // if z > 1024
                    return s * INFINITY;                     // Overflow
                else {
                    final double OVT     =  8.0085662595372944372e-0017; // -(1024-log2(ovfl+.5ulp))
                    if (p_l + OVT > z - p_h)
                        return s * INFINITY;   // Overflow
                }
            } else if ((j & 0x7fffffff) >= 0x4090cc00 ) {        // z <= -1075
                if (((j - 0xc090cc00) | i)!=0)           // z < -1075
                    return s * 0.0;           // Underflow
                else {
                    if (p_l <= z - p_h)
                        return s * 0.0;      // Underflow
                }
            }
            /*
             * Compute 2**(p_h+p_l)
             */
            // Poly coefs for (3/2)*(log(x)-2s-2/3*s**3
            final double P1      =  0x1.5555_5555_5553ep-3;  //  1.66666666666666019037e-01
            final double P2      = -0x1.6c16_c16b_ebd93p-9;  // -2.77777777770155933842e-03
            final double P3      =  0x1.1566_aaf2_5de2cp-14; //  6.61375632143793436117e-05
            final double P4      = -0x1.bbd4_1c5d_26bf1p-20; // -1.65339022054652515390e-06
            final double P5      =  0x1.6376_972b_ea4d0p-25; //  4.13813679705723846039e-08
            final double LG2     =  0x1.62e4_2fef_a39efp-1;  //  6.93147180559945286227e-01
            final double LG2_H   =  0x1.62e43p-1;            //  6.93147182464599609375e-01
            final double LG2_L   = -0x1.05c6_10ca_86c39p-29; // -1.90465429995776804525e-09
            i = j & 0x7fffffff;
            k = (i >> 20) - 0x3ff;
            n = 0;
            if (i > 0x3fe00000) {              // if |z| > 0.5, set n = [z + 0.5]
                n = j + (0x00100000 >> (k + 1));
                k = ((n & 0x7fffffff) >> 20) - 0x3ff;     // new k for n
                t = 0.0;
                t = __HI(t, (n & ~(0x000fffff >> k)) );
                n = ((n & 0x000fffff) | 0x00100000) >> (20 - k);
                if (j < 0)
                    n = -n;
                p_h -= t;
            }
            t = p_l + p_h;
            t = __LO(t, 0);
            u = t * LG2_H;
            v = (p_l - (t - p_h)) * LG2 + t * LG2_L;
            z = u + v;
            w = v - (z - u);
            t  = z * z;
            t1  = z - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))));
            r  = (z * t1)/(t1 - 2.0) - (w + z * w);
            z  = 1.0 - (r - z);
            j  = __HI(z);
            j += (n << 20);
            if ((j >> 20) <= 0)
                z = Math.scalb(z, n); // subnormal output
            else {
                int z_hi = __HI(z);
                z_hi += (n << 20);
                z = __HI(z, z_hi);
            }
            return s * z;
        }
    }

    /**
     * Returns the exponential of x.
     *
     * Method
     *   1. Argument reduction:
     *      Reduce x to an r so that |r| <= 0.5*ln2 ~ 0.34658.
     *      Given x, find r and integer k such that
     *
     *               x = k*ln2 + r,  |r| <= 0.5*ln2.
     *
     *      Here r will be represented as r = hi-lo for better
     *      accuracy.
     *
     *   2. Approximation of exp(r) by a special rational function on
     *      the interval [0,0.34658]:
     *      Write
     *          R(r**2) = r*(exp(r)+1)/(exp(r)-1) = 2 + r*r/6 - r**4/360 + ...
     *      We use a special Reme algorithm on [0,0.34658] to generate
     *      a polynomial of degree 5 to approximate R. The maximum error
     *      of this polynomial approximation is bounded by 2**-59. In
     *      other words,
     *          R(z) ~ 2.0 + P1*z + P2*z**2 + P3*z**3 + P4*z**4 + P5*z**5
     *      (where z=r*r, and the values of P1 to P5 are listed below)
     *      and
     *          |                  5          |     -59
     *          | 2.0+P1*z+...+P5*z   -  R(z) | <= 2
     *          |                             |
     *      The computation of exp(r) thus becomes
     *                             2*r
     *              exp(r) = 1 + -------
     *                            R - r
     *                                 r*R1(r)
     *                     = 1 + r + ----------- (for better accuracy)
     *                                2 - R1(r)
     *      where
     *                               2       4             10
     *              R1(r) = r - (P1*r  + P2*r  + ... + P5*r   ).
     *
     *   3. Scale back to obtain exp(x):
     *      From step 1, we have
     *         exp(x) = 2^k * exp(r)
     *
     * Special cases:
     *      exp(INF) is INF, exp(NaN) is NaN;
     *      exp(-INF) is 0, and
     *      for finite argument, only exp(0)=1 is exact.
     *
     * Accuracy:
     *      according to an error analysis, the error is always less than
     *      1 ulp (unit in the last place).
     *
     * Misc. info.
     *      For IEEE double
     *          if x >  7.09782712893383973096e+02 then exp(x) overflow
     *          if x < -7.45133219101941108420e+02 then exp(x) underflow
     *
     * Constants:
     * The hexadecimal values are the intended ones for the following
     * constants. The decimal values may be used, provided that the
     * compiler will convert from decimal to binary accurately enough
     * to produce the hexadecimal values shown.
     */
    static final class Exp {
        private Exp() {throw new UnsupportedOperationException();};

        private static final double one     = 1.0;
        private static final double[] half = {0.5, -0.5,};
        private static final double huge    = 1.0e+300;
        private static final double twom1000=     0x1.0p-1000;             //  9.33263618503218878990e-302 = 2^-1000
        private static final double o_threshold=  0x1.62e42fefa39efp9;     //  7.09782712893383973096e+02
        private static final double u_threshold= -0x1.74910d52d3051p9;     // -7.45133219101941108420e+02;
        private static final double[] ln2HI   ={  0x1.62e42feep-1,         //  6.93147180369123816490e-01
                                                 -0x1.62e42feep-1};        // -6.93147180369123816490e-01
        private static final double[] ln2LO   ={  0x1.a39ef35793c76p-33,   //  1.90821492927058770002e-10
                                                 -0x1.a39ef35793c76p-33};  // -1.90821492927058770002e-10
        private static final double invln2 =      0x1.71547652b82fep0;     //  1.44269504088896338700e+00

        private static final double P1   =  0x1.555555555553ep-3;  //  1.66666666666666019037e-01
        private static final double P2   = -0x1.6c16c16bebd93p-9;  // -2.77777777770155933842e-03
        private static final double P3   =  0x1.1566aaf25de2cp-14; //  6.61375632143793436117e-05
        private static final double P4   = -0x1.bbd41c5d26bf1p-20; // -1.65339022054652515390e-06
        private static final double P5   =  0x1.6376972bea4d0p-25; //  4.13813679705723846039e-08

        public static double compute(double x) {
            double y;
            double hi = 0.0;
            double lo = 0.0;
            double c;
            double t;
            int k = 0;
            int xsb;
            /*unsigned*/ int hx;

            hx  = __HI(x);  /* high word of x */
            xsb = (hx >> 31) & 1;               /* sign bit of x */
            hx &= 0x7fffffff;               /* high word of |x| */

            /* filter out non-finite argument */
            if (hx >= 0x40862E42) {                  /* if |x| >= 709.78... */
                if (hx >= 0x7ff00000) {
                    if (((hx & 0xfffff) | __LO(x)) != 0)
                        return x + x;                /* NaN */
                    else
                        return (xsb == 0) ? x : 0.0;    /* exp(+-inf) = {inf, 0} */
                }
                if (x > o_threshold)
                    return huge * huge; /* overflow */
                if (x < u_threshold) // unsigned compare needed here?
                    return twom1000 * twom1000; /* underflow */
            }

            /* argument reduction */
            if (hx > 0x3fd62e42) {           /* if  |x| > 0.5 ln2 */
                if(hx < 0x3FF0A2B2) {       /* and |x| < 1.5 ln2 */
                    hi = x - ln2HI[xsb];
                    lo=ln2LO[xsb];
                    k = 1 - xsb - xsb;
                } else {
                    k  = (int)(invln2 * x + half[xsb]);
                    t  = k;
                    hi = x - t*ln2HI[0];    /* t*ln2HI is exact here */
                    lo = t*ln2LO[0];
                }
                x  = hi - lo;
            } else if (hx < 0x3e300000)  {     /* when |x|<2**-28 */
                if (huge + x > one)
                    return one + x; /* trigger inexact */
            } else {
                k = 0;
            }

            /* x is now in primary range */
            t  = x * x;
            c  = x - t*(P1 + t*(P2 + t*(P3 + t*(P4 + t*P5))));
            if (k == 0)
                return one - ((x*c)/(c - 2.0) - x);
            else
                y = one - ((lo - (x*c)/(2.0 - c)) - hi);

            if(k >= -1021) {
                y = __HI(y, __HI(y) + (k << 20)); /* add k to y's exponent */
                return y;
            } else {
                y = __HI(y, __HI(y) + ((k + 1000) << 20)); /* add k to y's exponent */
                return y * twom1000;
            }
        }
    }

    /**
     * Return the (natural) logarithm of x
     *
     * Method :
     *   1. Argument Reduction: find k and f such that
     *                      x = 2^k * (1+f),
     *         where  sqrt(2)/2 < 1+f < sqrt(2) .
     *
     *   2. Approximation of log(1+f).
     *      Let s = f/(2+f) ; based on log(1+f) = log(1+s) - log(1-s)
     *               = 2s + 2/3 s**3 + 2/5 s**5 + .....,
     *               = 2s + s*R
     *      We use a special Reme algorithm on [0,0.1716] to generate
     *      a polynomial of degree 14 to approximate R The maximum error
     *      of this polynomial approximation is bounded by 2**-58.45. In
     *      other words,
     *                      2      4      6      8      10      12      14
     *          R(z) ~ Lg1*s +Lg2*s +Lg3*s +Lg4*s +Lg5*s  +Lg6*s  +Lg7*s
     *      (the values of Lg1 to Lg7 are listed in the program)
     *      and
     *          |      2          14          |     -58.45
     *          | Lg1*s +...+Lg7*s    -  R(z) | <= 2
     *          |                             |
     *      Note that 2s = f - s*f = f - hfsq + s*hfsq, where hfsq = f*f/2.
     *      In order to guarantee error in log below 1ulp, we compute log
     *      by
     *              log(1+f) = f - s*(f - R)        (if f is not too large)
     *              log(1+f) = f - (hfsq - s*(hfsq+R)).     (better accuracy)
     *
     *      3. Finally,  log(x) = k*ln2 + log(1+f).
     *                          = k*ln2_hi+(f-(hfsq-(s*(hfsq+R)+k*ln2_lo)))
     *         Here ln2 is split into two floating point number:
     *                      ln2_hi + ln2_lo,
     *         where n*ln2_hi is always exact for |n| < 2000.
     *
     * Special cases:
     *      log(x) is NaN with signal if x < 0 (including -INF) ;
     *      log(+INF) is +INF; log(0) is -INF with signal;
     *      log(NaN) is that NaN with no signal.
     *
     * Accuracy:
     *      according to an error analysis, the error is always less than
     *      1 ulp (unit in the last place).
     *
     * Constants:
     * The hexadecimal values are the intended ones for the following
     * constants. The decimal values may be used, provided that the
     * compiler will convert from decimal to binary accurately enough
     * to produce the hexadecimal values shown.
     */
    static final class Log {
        private Log() {throw new UnsupportedOperationException();}

        private static final double
            ln2_hi = 0x1.62e42feep-1,       // 6.93147180369123816490e-01
            ln2_lo = 0x1.a39ef35793c76p-33, // 1.90821492927058770002e-10

            Lg1    = 0x1.5555555555593p-1,  // 6.666666666666735130e-01
            Lg2    = 0x1.999999997fa04p-2,  // 3.999999999940941908e-01
            Lg3    = 0x1.2492494229359p-2,  // 2.857142874366239149e-01
            Lg4    = 0x1.c71c51d8e78afp-3,  // 2.222219843214978396e-01
            Lg5    = 0x1.7466496cb03dep-3,  // 1.818357216161805012e-01
            Lg6    = 0x1.39a09d078c69fp-3,  // 1.531383769920937332e-01
            Lg7    = 0x1.2f112df3e5244p-3;  // 1.479819860511658591e-01

        private static final double zero = 0.0;

        static double compute(double x) {
            double hfsq, f, s, z, R, w, t1, t2, dk;
            int k, hx, i, j;
            /*unsigned*/ int lx;

            hx = __HI(x);           // high word of x
            lx = __LO(x);           // low  word of x

            k=0;
            if (hx < 0x0010_0000) {                  // x < 2**-1022
                if (((hx & 0x7fff_ffff) | lx) == 0) { // log(+-0) = -inf
                    return -TWO54/zero;
                }
                if (hx < 0) {                        // log(-#) = NaN
                    return (x - x)/zero;
                }
                k -= 54;
                x *= TWO54;    // subnormal number, scale up x
                hx = __HI(x);  // high word of x
            }
            if (hx >= 0x7ff0_0000) {
                return x + x;
            }
            k += (hx >> 20) - 1023;
            hx &= 0x000f_ffff;
            i = (hx + 0x9_5f64) & 0x10_0000;
            x =__HI(x, hx | (i ^ 0x3ff0_0000));  // normalize x or x/2
            k += (i >> 20);
            f = x - 1.0;
            if ((0x000f_ffff & (2 + hx)) < 3) {// |f| < 2**-20
                if (f == zero) {
                    if (k == 0) {
                        return zero;
                    } else {
                        dk = (double)k;
                        return dk*ln2_hi + dk*ln2_lo;
                    }
                }
                R = f*f*(0.5 - 0.33333333333333333*f);
                if (k == 0) {
                    return f - R;
                } else {
                    dk = (double)k;
                    return dk*ln2_hi - ((R - dk*ln2_lo) - f);
                }
            }
            s = f/(2.0 + f);
            dk = (double)k;
            z = s*s;
            i = hx - 0x6_147a;
            w = z*z;
            j = 0x6b851 - hx;
            t1= w*(Lg2 + w*(Lg4 + w*Lg6));
            t2= z*(Lg1 + w*(Lg3 + w*(Lg5 + w*Lg7)));
            i |= j;
            R = t2 + t1;
            if (i > 0) {
                hfsq = 0.5*f*f;
                if (k == 0) {
                    return f-(hfsq - s*(hfsq + R));
                } else {
                    return dk*ln2_hi - ((hfsq - (s*(hfsq + R) + dk*ln2_lo)) - f);
                }
            } else {
                if (k == 0) {
                    return f - s*(f - R);
                } else {
                    return dk*ln2_hi - ((s*(f - R) - dk*ln2_lo) - f);
                }
            }
        }
    }

    /**
     * Return the base 10 logarithm of x
     *
     * Method :
     *      Let log10_2hi = leading 40 bits of log10(2) and
     *          log10_2lo = log10(2) - log10_2hi,
     *          ivln10   = 1/log(10) rounded.
     *      Then
     *              n = ilogb(x),
     *              if(n<0)  n = n+1;
     *              x = scalbn(x,-n);
     *              log10(x) := n*log10_2hi + (n*log10_2lo + ivln10*log(x))
     *
     * Note 1:
     *      To guarantee log10(10**n)=n, where 10**n is normal, the rounding
     *      mode must set to Round-to-Nearest.
     * Note 2:
     *      [1/log(10)] rounded to 53 bits has error  .198   ulps;
     *      log10 is monotonic at all binary break points.
     *
     * Special cases:
     *      log10(x) is NaN with signal if x < 0;
     *      log10(+INF) is +INF with no signal; log10(0) is -INF with signal;
     *      log10(NaN) is that NaN with no signal;
     *      log10(10**N) = N  for N=0,1,...,22.
     *
     * Constants:
     * The hexadecimal values are the intended ones for the following constants.
     * The decimal values may be used, provided that the compiler will convert
     * from decimal to binary accurately enough to produce the hexadecimal values
     * shown.
     */
    static class Log10 {
        private static final double ivln10    = 0x1.bcb7b1526e50ep-2;  // 4.34294481903251816668e-01

        private static final double log10_2hi = 0x1.34413509f6p-2;     // 3.01029995663611771306e-01;
        private static final double log10_2lo = 0x1.9fef311f12b36p-42; // 3.69423907715893078616e-13;

        private Log10() {
            throw new UnsupportedOperationException();
        }

        public static double compute(double x) {
            double y, z;
            int i, k;

            int hx = __HI(x); // high word of x
            int lx = __LO(x); // low word of x

            k=0;
            if (hx < 0x0010_0000) {                  /* x < 2**-1022  */
                if (((hx & 0x7fff_ffff) | lx) == 0) {
                    return -TWO54/0.0;               /* log(+-0)=-inf */
                }
                if (hx < 0) {
                    return (x - x)/0.0;              /* log(-#) = NaN */
                }
                k -= 54;
                x *= TWO54; /* subnormal number, scale up x */
                hx = __HI(x);
            }

            if (hx >= 0x7ff0_0000) {
                return x + x;
            }

            k += (hx >> 20) - 1023;
            i  = (k  & 0x8000_0000) >>> 31; // unsigned shift
            hx = (hx & 0x000f_ffff) | ((0x3ff - i) << 20);
            y  = (double)(k + i);
            x = __HI(x, hx); // replace high word of x with hx
            z  = y * log10_2lo + ivln10 * StrictMath.log(x);
            return  z + y * log10_2hi;
        }
    }

    /**
     * Returns the natural logarithm of the sum of the argument and 1.
     *
     * Method :
     *   1. Argument Reduction: find k and f such that
     *                      1+x = 2^k * (1+f),
     *         where  sqrt(2)/2 < 1+f < sqrt(2) .
     *
     *      Note. If k=0, then f=x is exact. However, if k!=0, then f
     *      may not be representable exactly. In that case, a correction
     *      term is need. Let u=1+x rounded. Let c = (1+x)-u, then
     *      log(1+x) - log(u) ~ c/u. Thus, we proceed to compute log(u),
     *      and add back the correction term c/u.
     *      (Note: when x > 2**53, one can simply return log(x))
     *
     *   2. Approximation of log1p(f).
     *      Let s = f/(2+f) ; based on log(1+f) = log(1+s) - log(1-s)
     *               = 2s + 2/3 s**3 + 2/5 s**5 + .....,
     *               = 2s + s*R
     *      We use a special Reme algorithm on [0,0.1716] to generate
     *      a polynomial of degree 14 to approximate R The maximum error
     *      of this polynomial approximation is bounded by 2**-58.45. In
     *      other words,
     *                      2      4      6      8      10      12      14
     *          R(z) ~ Lp1*s +Lp2*s +Lp3*s +Lp4*s +Lp5*s  +Lp6*s  +Lp7*s
     *      (the values of Lp1 to Lp7 are listed in the program)
     *      and
     *          |      2          14          |     -58.45
     *          | Lp1*s +...+Lp7*s    -  R(z) | <= 2
     *          |                             |
     *      Note that 2s = f - s*f = f - hfsq + s*hfsq, where hfsq = f*f/2.
     *      In order to guarantee error in log below 1ulp, we compute log
     *      by
     *              log1p(f) = f - (hfsq - s*(hfsq+R)).
     *
     *      3. Finally, log1p(x) = k*ln2 + log1p(f).
     *                           = k*ln2_hi+(f-(hfsq-(s*(hfsq+R)+k*ln2_lo)))
     *         Here ln2 is split into two floating point number:
     *                      ln2_hi + ln2_lo,
     *         where n*ln2_hi is always exact for |n| < 2000.
     *
     * Special cases:
     *      log1p(x) is NaN with signal if x < -1 (including -INF) ;
     *      log1p(+INF) is +INF; log1p(-1) is -INF with signal;
     *      log1p(NaN) is that NaN with no signal.
     *
     * Accuracy:
     *      according to an error analysis, the error is always less than
     *      1 ulp (unit in the last place).
     *
     * Constants:
     * The hexadecimal values are the intended ones for the following
     * constants. The decimal values may be used, provided that the
     * compiler will convert from decimal to binary accurately enough
     * to produce the hexadecimal values shown.
     *
     * Note: Assuming log() return accurate answer, the following
     *       algorithm can be used to compute log1p(x) to within a few ULP:
     *
     *              u = 1+x;
     *              if(u==1.0) return x ; else
     *                         return log(u)*(x/(u-1.0));
     *
     *       See HP-15C Advanced Functions Handbook, p.193.
     */
    static class Log1p {
        private static final double ln2_hi = 0x1.62e42feep-1;       // 6.93147180369123816490e-01
        private static final double ln2_lo = 0x1.a39ef35793c76p-33; // 1.90821492927058770002e-10
        private static final double Lp1    = 0x1.5555555555593p-1;  // 6.666666666666735130e-01
        private static final double Lp2    = 0x1.999999997fa04p-2;  // 3.999999999940941908e-01
        private static final double Lp3    = 0x1.2492494229359p-2;  // 2.857142874366239149e-01
        private static final double Lp4    = 0x1.c71c51d8e78afp-3;  // 2.222219843214978396e-01
        private static final double Lp5    = 0x1.7466496cb03dep-3;  // 1.818357216161805012e-01
        private static final double Lp6    = 0x1.39a09d078c69fp-3;  // 1.531383769920937332e-01
        private static final double Lp7    = 0x1.2f112df3e5244p-3;  // 1.479819860511658591e-01

        public static double compute(double x) {
            double hfsq, f=0, c=0, s, z, R, u;
            int k, hx, hu=0, ax;

            hx = __HI(x);           /* high word of x */
            ax = hx & 0x7fff_ffff;

            k = 1;
            if (hx < 0x3FDA_827A) {                  /* x < 0.41422  */
                if (ax >= 0x3ff0_0000) {             /* x <= -1.0 */
                    if (x == -1.0) /* log1p(-1)=-inf */
                        return -INFINITY;
                    else
                        return Double.NaN;           /* log1p(x < -1) = NaN */
                }

                if (ax < 0x3e20_0000) {                /* |x| < 2**-29 */
                    if (TWO54 + x > 0.0                /* raise inexact */
                       && ax < 0x3c90_0000)            /* |x| < 2**-54 */
                        return x;
                    else
                        return x - x*x*0.5;
                }

                if (hx > 0 || hx <= 0xbfd2_bec3) { /* -0.2929 < x < 0.41422 */
                    k=0;
                    f=x;
                    hu=1;
                }
            }

            if (hx >= 0x7ff0_0000) {
                return x + x;
            }

            if (k != 0) {
                if (hx < 0x4340_0000) {
                    u  = 1.0 + x;
                    hu = __HI(u);           /* high word of u */
                    k  = (hu >> 20) - 1023;
                    c  = (k > 0)? 1.0 - (u-x) : x-(u-1.0); /* correction term */
                    c /= u;
                } else {
                    u  = x;
                    hu = __HI(u);           /* high word of u */
                    k  = (hu >> 20) - 1023;
                    c  = 0;
                }
                hu &= 0x000f_ffff;
                if (hu < 0x6_a09e) {
                    u = __HI(u, hu | 0x3ff0_0000);       /* normalize u */
                } else {
                    k += 1;
                    u = __HI(u, hu | 0x3fe0_0000);       /* normalize u/2 */
                    hu = (0x0010_0000 - hu) >> 2;
                }
                f = u - 1.0;
            }

            hfsq = 0.5*f*f;
            if (hu == 0) {     /* |f| < 2**-20 */
                if (f == 0.0) {
                    if (k == 0) {
                        return 0.0;
                    } else {
                        c += k * ln2_lo;
                        return k * ln2_hi + c;
                    }
                }
                R = hfsq * (1.0 - 0.66666666666666666*f);
                if (k == 0) {
                    return f - R;
                } else {
                    return k * ln2_hi - ((R-(k * ln2_lo+c)) - f);
                }
            }
            s = f/(2.0 + f);
            z = s * s;
            R = z * (Lp1 + z * (Lp2 + z * (Lp3 + z * (Lp4 + z * (Lp5 + z * (Lp6 + z*Lp7))))));
            if (k == 0) {
                return f - (hfsq - s*(hfsq + R));
            } else {
                return k * ln2_hi - ((hfsq - (s*(hfsq + R) + (k * ln2_lo+c))) - f);
            }
        }
    }

    /* expm1(x)
     * Returns exp(x)-1, the exponential of x minus 1.
     *
     * Method
     *   1. Argument reduction:
     *      Given x, find r and integer k such that
     *
     *               x = k*ln2 + r,  |r| <= 0.5*ln2 ~ 0.34658
     *
     *      Here a correction term c will be computed to compensate
     *      the error in r when rounded to a floating-point number.
     *
     *   2. Approximating expm1(r) by a special rational function on
     *      the interval [0,0.34658]:
     *      Since
     *          r*(exp(r)+1)/(exp(r)-1) = 2+ r^2/6 - r^4/360 + ...
     *      we define R1(r*r) by
     *          r*(exp(r)+1)/(exp(r)-1) = 2+ r^2/6 * R1(r*r)
     *      That is,
     *          R1(r**2) = 6/r *((exp(r)+1)/(exp(r)-1) - 2/r)
     *                   = 6/r * ( 1 + 2.0*(1/(exp(r)-1) - 1/r))
     *                   = 1 - r^2/60 + r^4/2520 - r^6/100800 + ...
     *      We use a special Reme algorithm on [0,0.347] to generate
     *      a polynomial of degree 5 in r*r to approximate R1. The
     *      maximum error of this polynomial approximation is bounded
     *      by 2**-61. In other words,
     *          R1(z) ~ 1.0 + Q1*z + Q2*z**2 + Q3*z**3 + Q4*z**4 + Q5*z**5
     *      where   Q1  =  -1.6666666666666567384E-2,
     *              Q2  =   3.9682539681370365873E-4,
     *              Q3  =  -9.9206344733435987357E-6,
     *              Q4  =   2.5051361420808517002E-7,
     *              Q5  =  -6.2843505682382617102E-9;
     *      (where z=r*r, and the values of Q1 to Q5 are listed below)
     *      with error bounded by
     *          |                  5           |     -61
     *          | 1.0+Q1*z+...+Q5*z   -  R1(z) | <= 2
     *          |                              |
     *
     *      expm1(r) = exp(r)-1 is then computed by the following
     *      specific way which minimize the accumulation rounding error:
     *                             2     3
     *                            r     r    [ 3 - (R1 + R1*r/2)  ]
     *            expm1(r) = r + --- + --- * [--------------------]
     *                            2     2    [ 6 - r*(3 - R1*r/2) ]
     *
     *      To compensate the error in the argument reduction, we use
     *              expm1(r+c) = expm1(r) + c + expm1(r)*c
     *                         ~ expm1(r) + c + r*c
     *      Thus c+r*c will be added in as the correction terms for
     *      expm1(r+c). Now rearrange the term to avoid optimization
     *      screw up:
     *                      (      2                                    2 )
     *                      ({  ( r    [ R1 -  (3 - R1*r/2) ]  )  }    r  )
     *       expm1(r+c)~r - ({r*(--- * [--------------------]-c)-c} - --- )
     *                      ({  ( 2    [ 6 - r*(3 - R1*r/2) ]  )  }    2  )
     *                      (                                             )
     *
     *                 = r - E
     *   3. Scale back to obtain expm1(x):
     *      From step 1, we have
     *         expm1(x) = either 2^k*[expm1(r)+1] - 1
     *                  = or     2^k*[expm1(r) + (1-2^-k)]
     *   4. Implementation notes:
     *      (A). To save one multiplication, we scale the coefficient Qi
     *           to Qi*2^i, and replace z by (x^2)/2.
     *      (B). To achieve maximum accuracy, we compute expm1(x) by
     *        (i)   if x < -56*ln2, return -1.0, (raise inexact if x!=inf)
     *        (ii)  if k=0, return r-E
     *        (iii) if k=-1, return 0.5*(r-E)-0.5
     *        (iv)  if k=1 if r < -0.25, return 2*((r+0.5)- E)
     *                     else          return  1.0+2.0*(r-E);
     *        (v)   if (k<-2||k>56) return 2^k(1-(E-r)) - 1 (or exp(x)-1)
     *        (vi)  if k <= 20, return 2^k((1-2^-k)-(E-r)), else
     *        (vii) return 2^k(1-((E+2^-k)-r))
     *
     * Special cases:
     *      expm1(INF) is INF, expm1(NaN) is NaN;
     *      expm1(-INF) is -1, and
     *      for finite argument, only expm1(0)=0 is exact.
     *
     * Accuracy:
     *      according to an error analysis, the error is always less than
     *      1 ulp (unit in the last place).
     *
     * Misc. info.
     *      For IEEE double
     *          if x >  7.09782712893383973096e+02 then expm1(x) overflow
     *
     * Constants:
     * The hexadecimal values are the intended ones for the following
     * constants. The decimal values may be used, provided that the
     * compiler will convert from decimal to binary accurately enough
     * to produce the hexadecimal values shown.
     */
    static class Expm1 {
        private static final double one         =  1.0;
        private static final double huge        =  1.0e+300;
        private static final double tiny        =  1.0e-300;
        private static final double o_threshold =  0x1.62e42fefa39efp9;   //  7.09782712893383973096e+02
        private static final double ln2_hi      =  0x1.62e42feep-1;       //  6.93147180369123816490e-01
        private static final double ln2_lo      =  0x1.a39ef35793c76p-33; //  1.90821492927058770002e-10
        private static final double invln2      =  0x1.71547652b82fep0;   //  1.44269504088896338700e+00
        // scaled coefficients related to expm1
        private static final double Q1          = -0x1.11111111110f4p-5;  // -3.33333333333331316428e-02
        private static final double Q2          =  0x1.a01a019fe5585p-10; //  1.58730158725481460165e-03
        private static final double Q3          = -0x1.4ce199eaadbb7p-14; // -7.93650757867487942473e-05
        private static final double Q4          =  0x1.0cfca86e65239p-18; //  4.00821782732936239552e-06
        private static final double Q5          = -0x1.afdb76e09c32dp-23; // -2.01099218183624371326e-07

        static double compute(double x) {
            double y, hi, lo, c=0, t, e, hxs, hfx, r1;
            int k, xsb;
            /*unsigned*/ int hx;

            hx  = __HI(x);  // high word of x
            xsb = hx & 0x8000_0000;          // sign bit of x
            y = Math.abs(x);
            hx &= 0x7fff_ffff;               // high word of |x|

            // filter out huge and non-finite argument
            if (hx >= 0x4043_687A) {                  // if |x| >= 56*ln2
                if (hx >= 0x4086_2E42) {              // if |x| >= 709.78...
                    if (hx >= 0x7ff_00000) {
                        if (((hx & 0xf_ffff) | __LO(x)) != 0) {
                            return x + x;     // NaN
                        } else {
                            return (xsb == 0)? x : -1.0; // exp(+-inf)={inf,-1}
                        }
                    }
                    if (x > o_threshold) {
                        return huge*huge; // overflow
                    }
                }
                if (xsb != 0) { // x < -56*ln2, return -1.0 with inexact
                    if (x + tiny < 0.0) {         // raise inexact
                        return tiny - one;        // return -1
                    }
                }
            }

            // argument reduction
            if (hx > 0x3fd6_2e42) {         // if  |x| > 0.5 ln2
                if (hx < 0x3FF0_A2B2) {     // and |x| < 1.5 ln2
                    if (xsb == 0) {
                        hi = x - ln2_hi;
                        lo = ln2_lo;
                        k =  1;
                    } else {
                        hi = x + ln2_hi;
                        lo = -ln2_lo;
                        k = -1;
                    }
                } else {
                    k  = (int)(invln2*x + ((xsb == 0) ? 0.5 : -0.5));
                    t  = k;
                    hi = x - t*ln2_hi;      // t*ln2_hi is exact here
                    lo = t*ln2_lo;
                }
                x  = hi - lo;
                c  = (hi - x) - lo;
            } else if (hx < 0x3c90_0000) {  // when |x| < 2**-54, return x
                t = huge + x; // return x with inexact flags when x != 0
                return x - (t - (huge + x));
            } else {
                k = 0;
            }

            // x is now in primary range
            hfx = 0.5*x;
            hxs = x*hfx;
            r1 = one + hxs*(Q1 + hxs*(Q2 + hxs*(Q3 + hxs*(Q4 + hxs*Q5))));
            t  = 3.0 - r1*hfx;
            e  = hxs *((r1 - t)/(6.0 - x*t));
            if (k == 0) {
                return x - (x*e - hxs);          // c is 0
            } else {
                e  = (x*(e - c) - c);
                e -= hxs;
                if (k == -1) {
                    return 0.5*(x - e) - 0.5;
                }
                if (k == 1) {
                    if (x < -0.25) {
                        return -2.0*(e - (x + 0.5));
                    } else {
                        return one + 2.0*(x - e);
                    }
                }
                if (k <= -2 || k > 56) {   // suffice to return exp(x) - 1
                    y = one - (e - x);
                    y = __HI(y, __HI(y) + (k << 20));     // add k to y's exponent
                    return y - one;
                }
                t = one;
                if (k < 20) {
                    t = __HI(t, 0x3ff0_0000 - (0x2_00000 >> k));  // t = 1-2^-k
                    y = t - ( e - x);
                    y = __HI(y, __HI(y) + (k << 20));     // add k to y's exponent
                } else {
                    t = __HI(t, ((0x3ff - k) << 20));     // 2^-k
                    y = x - (e + t);
                    y += one;
                    y = __HI(y, __HI(y) + (k << 20));     // add k to y's exponent
                }
            }
            return y;
        }
    }

    /**
     * Method :
     * mathematically sinh(x) if defined to be (exp(x)-exp(-x))/2
     *      1. Replace x by |x| (sinh(-x) = -sinh(x)).
     *      2.
     *                                                  E + E/(E+1)
     *          0        <= x <= 22     :  sinh(x) := --------------, E=expm1(x)
     *                                                      2
     *
     *          22       <= x <= lnovft :  sinh(x) := exp(x)/2
     *          lnovft   <= x <= ln2ovft:  sinh(x) := exp(x/2)/2 * exp(x/2)
     *          ln2ovft  <  x           :  sinh(x) := x*shuge (overflow)
     *
     * Special cases:
     *      sinh(x) is |x| if x is +INF, -INF, or NaN.
     *      only sinh(0)=0 is exact for finite x.
     */
    static final class Sinh {
        private Sinh() {throw new UnsupportedOperationException();}

        private static final double one = 1.0, shuge = 1.0e307;

         static double compute(double x) {
            double t, w, h;
            int ix, jx;
            /* unsigned */ int lx;

            // High word of |x|
            jx = __HI(x);
            ix = jx & 0x7fff_ffff;

            // x is INF or NaN
            if (ix >= 0x7ff0_0000) {
                return x + x;
            }

            h = 0.5;
            if (jx < 0) {
                h = -h;
            }
            // |x| in [0,22], return sign(x)*0.5*(E+E/(E+1)))
            if (ix < 0x4036_0000) {          // |x| < 22
                if (ix < 0x3e30_0000)        // |x| < 2**-28
                    if (shuge + x > one) {   // sinh(tiny) = tiny with inexact
                        return x;
                    }
                t = StrictMath.expm1(Math.abs(x));
                if (ix < 0x3ff0_0000) {
                    return h*(2.0 * t - t*t/(t + one));
                }
                return h*(t + t/(t + one));
            }

            // |x| in [22, log(maxdouble)] return 0.5*exp(|x|)
            if (ix < 0x4086_2E42) {
                return h*StrictMath.exp(Math.abs(x));
            }

            // |x| in [log(maxdouble), overflowthresold]
            lx = __LO(x);
            if (ix < 0x4086_33CE ||
                ((ix == 0x4086_33ce) &&
                 (Long.compareUnsigned(lx, 0x8fb9_f87d) <= 0 ))) {
                w = StrictMath.exp(0.5 * Math.abs(x));
                t = h * w;
                return t * w;
            }

            // |x| > overflowthresold, sinh(x) overflow
            return x * shuge;
        }
    }

    /**
     * Method :
     * mathematically cosh(x) if defined to be (exp(x)+exp(-x))/2
     *      1. Replace x by |x| (cosh(x) = cosh(-x)).
     *      2.
     *                                                      [ exp(x) - 1 ]^2
     *          0        <= x <= ln2/2  :  cosh(x) := 1 + -------------------
     *                                                         2*exp(x)
     *
     *                                                exp(x) +  1/exp(x)
     *          ln2/2    <= x <= 22     :  cosh(x) := -------------------
     *                                                        2
     *          22       <= x <= lnovft :  cosh(x) := exp(x)/2
     *          lnovft   <= x <= ln2ovft:  cosh(x) := exp(x/2)/2 * exp(x/2)
     *          ln2ovft  <  x           :  cosh(x) := huge*huge (overflow)
     *
     * Special cases:
     *      cosh(x) is |x| if x is +INF, -INF, or NaN.
     *      only cosh(0)=1 is exact for finite x.
     */
    static final class Cosh {
        private Cosh() {throw new UnsupportedOperationException();}

        private static final double one = 1.0, half=0.5, huge = 1.0e300;
        static double compute(double x) {
            double t, w;
            int ix;
            /*unsigned*/ int lx;

            // High word of |x|
            ix = __HI(x);
            ix &= 0x7fff_ffff;

            // x is INF or NaN
            if (ix >= 0x7ff0_0000) {
                return x*x;
            }

            // |x| in [0,0.5*ln2], return 1+expm1(|x|)^2/(2*exp(|x|))
            if (ix < 0x3fd6_2e43) {
                t = StrictMath.expm1(Math.abs(x));
                w = one + t;
                if (ix < 0x3c80_0000) { // cosh(tiny) = 1
                    return w;
                }
                return one + (t * t)/(w + w);
            }

            // |x| in [0.5*ln2, 22], return (exp(|x|) + 1/exp(|x|)/2
            if (ix < 0x4036_0000) {
                t = StrictMath.exp(Math.abs(x));
                return half*t + half/t;
            }

            // |x| in [22, log(maxdouble)] return half*exp(|x|)
            if (ix < 0x4086_2E42) {
                return half*StrictMath.exp(Math.abs(x));
            }

            // |x| in [log(maxdouble), overflowthresold]
            lx = __LO(x);
            if (ix<0x4086_33CE ||
                ((ix == 0x4086_33ce) &&
                 (Integer.compareUnsigned(lx, 0x8fb9_f87d) <= 0))) {
                w = StrictMath.exp(half*Math.abs(x));
                t = half*w;
                return t*w;
            }

            // |x| > overflowthresold, cosh(x) overflow
            return huge*huge;
        }
    }
    /**
     * Return the Hyperbolic Tangent of x
     *
     * Method :
     *                                     x    -x
     *                                    e  - e
     *      0. tanh(x) is defined to be -----------
     *                                     x    -x
     *                                    e  + e
     *      1. reduce x to non-negative by tanh(-x) = -tanh(x).
     *      2.  0      <= x <= 2**-55 : tanh(x) := x*(one+x)
     *                                              -t
     *          2**-55 <  x <=  1     : tanh(x) := -----; t = expm1(-2x)
     *                                             t + 2
     *                                                   2
     *          1      <= x <=  22.0  : tanh(x) := 1-  ----- ; t=expm1(2x)
     *                                                 t + 2
     *          22.0   <  x <= INF    : tanh(x) := 1.
     *
     * Special cases:
     *      tanh(NaN) is NaN;
     *      only tanh(0)=0 is exact for finite argument.
     */
    static final class Tanh {
        private Tanh() {throw new UnsupportedOperationException();}

        private static final double one=1.0, two=2.0, tiny = 1.0e-300;
        static double compute(double x) {
            double t, z;
            int jx, ix;

            // High word of |x|.
            jx = __HI(x);
            ix = jx & 0x7fff_ffff;

            // x is INF or NaN
            if (ix >= 0x7ff0_0000) {
                if (jx >= 0) {  // tanh(+-inf)=+-1
                    return one/x + one;
                } else {        // tanh(NaN) = NaN
                    return one/x - one;
                }
            }

            // |x| < 22
            if (ix < 0x4036_0000) {          // |x| < 22
                if (ix<0x3c80_0000)          // |x| < 2**-55
                    return x*(one + x);      // tanh(small) = small
                if (ix>=0x3ff0_0000) {       // |x| >= 1
                    t = StrictMath.expm1(two*Math.abs(x));
                    z = one - two/(t + two);
                } else {
                    t = StrictMath.expm1(-two*Math.abs(x));
                    z= -t/(t + two);
                }
            } else { // |x| > 22, return +-1
                z = one - tiny;             // raised inexact flag
            }
            return (jx >= 0)? z: -z;
        }
    }
}
