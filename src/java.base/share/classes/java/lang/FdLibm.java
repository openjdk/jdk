/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.Stable;

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
final class FdLibm {
    // Constants used by multiple algorithms
    private static final double INFINITY = Double.POSITIVE_INFINITY;
    private static final double TWO24    = 0x1.0p24; // 1.67772160000000000000e+07
    private static final double TWO54    = 0x1.0p54; // 1.80143985094819840000e+16
    private static final double HUGE     = 1.0e+300;

    /*
     * Constants for bit-wise manipulation of IEEE 754 double
     * values. These constants are for the high-order 32-bits of a
     * 64-bit double value: 1 sign bit as the most significant bit,
     * followed by 11 exponent bits, and then the remaining bits as
     * the significand.
     */
    private static final int SIGN_BIT        = 0x8000_0000;
    private static final int EXP_BITS        = 0x7ff0_0000;
    private static final int EXP_SIGNIF_BITS = 0x7fff_ffff;

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
     * Return a double with its high-order bits of the first argument
     * and the low-order bits of the second argument..
     */
    private static double __HI_LO(int high, int low) {
        return Double.longBitsToDouble(((long)high << 32) |
                                       (low & 0xffff_ffffL));
    }

    /** sin(x)
     * Return sine function of x.
     *
     * kernel function:
     *      __kernel_sin            ... sine function on [-pi/4,pi/4]
     *      __kernel_cos            ... cose function on [-pi/4,pi/4]
     *      __ieee754_rem_pio2      ... argument reduction routine
     *
     * Method.
     *      Let S,C and T denote the sin, cos and tan respectively on
     *      [-PI/4, +PI/4]. Reduce the argument x to y1+y2 = x-k*pi/2
     *      in [-pi/4 , +pi/4], and let n = k mod 4.
     *      We have
     *
     *          n        sin(x)      cos(x)        tan(x)
     *     ----------------------------------------------------------
     *          0          S           C             T
     *          1          C          -S            -1/T
     *          2         -S          -C             T
     *          3         -C           S            -1/T
     *     ----------------------------------------------------------
     *
     * Special cases:
     *      Let trig be any of sin, cos, or tan.
     *      trig(+-INF)  is NaN, with signals;
     *      trig(NaN)    is that NaN;
     *
     * Accuracy:
     *      TRIG(x) returns trig(x) nearly rounded
     */
    static final class Sin {
        private Sin() {throw new UnsupportedOperationException();}

        static double compute(double x) {
            double[] y = new double[2];
            double z = 0.0;
            int n, ix;

            // High word of x.
            ix = __HI(x);

            // |x| ~< pi/4
            ix &= EXP_SIGNIF_BITS;
            if (ix <= 0x3fe9_21fb) {
                return __kernel_sin(x, z, 0);
            } else if (ix >= EXP_BITS) {  // sin(Inf or NaN) is NaN
                return x - x;
            } else { // argument reduction needed
                n = RemPio2.__ieee754_rem_pio2(x, y);
                switch(n & 3) {
                case 0: return  Sin.__kernel_sin(y[0], y[1], 1);
                case 1: return  Cos.__kernel_cos(y[0], y[1]);
                case 2: return -Sin.__kernel_sin(y[0], y[1], 1);
                default:
                    return -Cos.__kernel_cos(y[0], y[1]);
                }
            }
        }

        /** __kernel_sin( x, y, iy)
         * kernel sin function on [-pi/4, pi/4], pi/4 ~ 0.7854
         * Input x is assumed to be bounded by ~pi/4 in magnitude.
         * Input y is the tail of x.
         * Input iy indicates whether y is 0. (if iy=0, y assume to be 0).
         *
         * Algorithm
         *      1. Since sin(-x) = -sin(x), we need only to consider positive x.
         *      2. if x < 2^-27 (hx<0x3e400000 0), return x with inexact if x!=0.
         *      3. sin(x) is approximated by a polynomial of degree 13 on
         *         [0,pi/4]
         *                               3            13
         *              sin(x) ~ x + S1*x + ... + S6*x
         *         where
         *
         *      |sin(x)         2     4     6     8     10     12  |     -58
         *      |----- - (1+S1*x +S2*x +S3*x +S4*x +S5*x  +S6*x   )| <= 2
         *      |  x                                               |
         *
         *      4. sin(x+y) = sin(x) + sin'(x')*y
         *                  ~ sin(x) + (1-x*x/2)*y
         *         For better accuracy, let
         *                   3      2      2      2      2
         *              r = x *(S2+x *(S3+x *(S4+x *(S5+x *S6))))
         *         then                   3    2
         *              sin(x) = x + (S1*x + (x *(r-y/2)+y))
         */
        private static final double
            S1  = -0x1.5555555555549p-3,  // -1.66666666666666324348e-01
            S2  =  0x1.111111110f8a6p-7,  //  8.33333333332248946124e-03
            S3  = -0x1.a01a019c161d5p-13, // -1.98412698298579493134e-04
            S4  =  0x1.71de357b1fe7dp-19, //  2.75573137070700676789e-06
            S5  = -0x1.ae5e68a2b9cebp-26, // -2.50507602534068634195e-08
            S6  =  0x1.5d93a5acfd57cp-33; //  1.58969099521155010221e-10

        static double __kernel_sin(double x, double y, int iy) {
            double z, r, v;
            int ix;
            ix = __HI(x) & EXP_SIGNIF_BITS;    // high word of x
            if (ix < 0x3e40_0000) {            // |x| < 2**-27
                if ((int)x == 0)               // generate inexact
                    return x;
            }
            z       =  x*x;
            v       =  z*x;
            r       =  S2 + z*(S3 + z*(S4 + z*(S5 + z*S6)));
            if (iy == 0) {
                return x + v*(S1 + z*r);
            } else {
                return x - ((z*(0.5*y - v*r) - y) - v*S1);
            }
        }
    }

    /** cos(x)
     * Return cosine function of x.
     *
     * kernel function:
     *      __kernel_sin            ... sine function on [-pi/4,pi/4]
     *      __kernel_cos            ... cosine function on [-pi/4,pi/4]
     *      __ieee754_rem_pio2      ... argument reduction routine
     *
     * Method.
     *      Let S,C and T denote the sin, cos and tan respectively on
     *      [-PI/4, +PI/4]. Reduce the argument x to y1+y2 = x-k*pi/2
     *      in [-pi/4 , +pi/4], and let n = k mod 4.
     *      We have
     *
     *          n        sin(x)      cos(x)        tan(x)
     *     ----------------------------------------------------------
     *          0          S           C             T
     *          1          C          -S            -1/T
     *          2         -S          -C             T
     *          3         -C           S            -1/T
     *     ----------------------------------------------------------
     *
     * Special cases:
     *      Let trig be any of sin, cos, or tan.
     *      trig(+-INF)  is NaN, with signals;
     *      trig(NaN)    is that NaN;
     *
     * Accuracy:
     *      TRIG(x) returns trig(x) nearly rounded
     */
    static final class Cos {
        private Cos() {throw new UnsupportedOperationException();}

        static double compute(double x) {
            double[] y = new double[2];
            double z = 0.0;
            int n, ix;

            // High word of x.
            ix = __HI(x);

            // |x| ~< pi/4
            ix &= EXP_SIGNIF_BITS;
            if (ix <= 0x3fe9_21fb) {
                return __kernel_cos(x, z);
            } else if (ix >= EXP_BITS) { // cos(Inf or NaN) is NaN
                return x - x;
            } else { // argument reduction needed
                n = RemPio2.__ieee754_rem_pio2(x,y);
                switch (n & 3) {
                case 0: return  Cos.__kernel_cos(y[0], y[1]);
                case 1: return -Sin.__kernel_sin(y[0], y[1],1);
                case 2: return -Cos.__kernel_cos(y[0], y[1]);
                default:
                    return  Sin.__kernel_sin(y[0], y[1], 1);
                }
            }
        }

        /**
         * __kernel_cos( x,  y )
         * kernel cos function on [-pi/4, pi/4], pi/4 ~ 0.785398164
         * Input x is assumed to be bounded by ~pi/4 in magnitude.
         * Input y is the tail of x.
         *
         * Algorithm
         *      1. Since cos(-x) = cos(x), we need only to consider positive x.
         *      2. if x < 2^-27 (hx < 0x3e4000000), return 1 with inexact if x != 0.
         *      3. cos(x) is approximated by a polynomial of degree 14 on
         *         [0,pi/4]
         *                                       4            14
         *              cos(x) ~ 1 - x*x/2 + C1*x + ... + C6*x
         *         where the remez error is
         *
         *      |              2     4     6     8     10    12     14 |     -58
         *      |cos(x)-(1-.5*x +C1*x +C2*x +C3*x +C4*x +C5*x  +C6*x  )| <= 2
         *      |                                                      |
         *
         *                     4     6     8     10    12     14
         *      4. let r = C1*x +C2*x +C3*x +C4*x +C5*x  +C6*x  , then
         *             cos(x) = 1 - x*x/2 + r
         *         since cos(x+y) ~ cos(x) - sin(x)*y
         *                        ~ cos(x) - x*y,
         *         a correction term is necessary in cos(x) and hence
         *              cos(x+y) = 1 - (x*x/2 - (r - x*y))
         *         For better accuracy when x > 0.3, let qx = |x|/4 with
         *         the last 32 bits mask off, and if x > 0.78125, let qx = 0.28125.
         *         Then
         *              cos(x+y) = (1-qx) - ((x*x/2-qx) - (r-x*y)).
         *         Note that 1-qx and (x*x/2-qx) is EXACT here, and the
         *         magnitude of the latter is at least a quarter of x*x/2,
         *         thus, reducing the rounding error in the subtraction.
         */
        private static final double
            C1  =  0x1.555555555554cp-5,  //  4.16666666666666019037e-02
            C2  = -0x1.6c16c16c15177p-10, // -1.38888888888741095749e-03
            C3  =  0x1.a01a019cb159p-16,  //  2.48015872894767294178e-05
            C4  = -0x1.27e4f809c52adp-22, // -2.75573143513906633035e-07
            C5  =  0x1.1ee9ebdb4b1c4p-29, //  2.08757232129817482790e-09
            C6  = -0x1.8fae9be8838d4p-37; // -1.13596475577881948265e-11

        static double __kernel_cos(double x, double y) {
            double a, hz, z, r, qx = 0.0;
            int ix;
            ix = __HI(x) & EXP_SIGNIF_BITS;       // ix = |x|'s high word
            if (ix < 0x3e40_0000) {           // if x < 2**27
                if (((int)x) == 0) {          // generate inexact
                    return 1.0;
                }
            }
            z  = x*x;
            r  = z*(C1 + z*(C2 + z*(C3 + z*(C4 + z*(C5 + z*C6)))));
            if (ix < 0x3FD3_3333) {                    // if |x| < 0.3
                return 1.0 - (0.5*z - (z*r - x*y));
            } else {
                if (ix > 0x3fe9_0000) {               // x > 0.78125
                    qx = 0.28125;
                } else {
                    qx = __HI_LO(ix - 0x0020_0000, 0);
                }
                hz = 0.5*z - qx;
                a  = 1.0 - qx;
                return a - (hz - (z*r - x*y));
            }
        }
    }

    /** tan(x)
     * Return tangent function of x.
     *
     * kernel function:
     *      __kernel_tan            ... tangent function on [-pi/4,pi/4]
     *      __ieee754_rem_pio2      ... argument reduction routine
     *
     * Method.
     *      Let S,C and T denote the sin, cos and tan respectively on
     *      [-PI/4, +PI/4]. Reduce the argument x to y1+y2 = x-k*pi/2
     *      in [-pi/4 , +pi/4], and let n = k mod 4.
     *      We have
     *
     *          n        sin(x)      cos(x)        tan(x)
     *     ----------------------------------------------------------
     *          0          S           C             T
     *          1          C          -S            -1/T
     *          2         -S          -C             T
     *          3         -C           S            -1/T
     *     ----------------------------------------------------------
     *
     * Special cases:
     *      Let trig be any of sin, cos, or tan.
     *      trig(+-INF)  is NaN, with signals;
     *      trig(NaN)    is that NaN;
     *
     * Accuracy:
     *      TRIG(x) returns trig(x) nearly rounded
     */
    static final class Tan {
        private Tan() {throw new UnsupportedOperationException();}

        static double compute(double x) {
            double[] y = new double[2];
            double z = 0.0;
            int n, ix;

            // High word of x.
            ix = __HI(x);

            // |x| ~< pi/4
            ix &= EXP_SIGNIF_BITS;
            if (ix <= 0x3fe9_21fb) {
                return __kernel_tan(x, z, 1);
            } else if (ix >= EXP_BITS) { // tan(Inf or NaN) is NaN
                return x - x;            // NaN
            } else {           // argument reduction needed
                n = RemPio2.__ieee754_rem_pio2(x, y);
                return __kernel_tan(y[0], y[1], 1 - ((n & 1) << 1)); // 1 -- n even; -1 -- n odd
            }
        }

        /** __kernel_tan( x, y, k )
         * kernel tan function on [-pi/4, pi/4], pi/4 ~ 0.7854
         * Input x is assumed to be bounded by ~pi/4 in magnitude.
         * Input y is the tail of x.
         * Input k indicates whether tan (if k=1) or
         * -1/tan (if k= -1) is returned.
         *
         * Algorithm
         *      1. Since tan(-x) = -tan(x), we need only to consider positive x.
         *      2. if x < 2^-28 (hx < 0x3e300000 0), return x with inexact if x != 0.
         *      3. tan(x) is approximated by a odd polynomial of degree 27 on
         *         [0, 0.67434]
         *                               3             27
         *              tan(x) ~ x + T1*x + ... + T13*x
         *         where
         *
         *              |tan(x)         2     4            26   |     -59.2
         *              |----- - (1+T1*x +T2*x +.... +T13*x    )| <= 2
         *              |  x                                    |
         *
         *         Note: tan(x+y) = tan(x) + tan'(x)*y
         *                        ~ tan(x) + (1+x*x)*y
         *         Therefore, for better accuracy in computing tan(x+y), let
         *                   3      2      2       2       2
         *              r = x *(T2+x *(T3+x *(...+x *(T12+x *T13))))
         *         then
         *                                  3    2
         *              tan(x+y) = x + (T1*x + (x *(r+y)+y))
         *
         *      4. For x in [0.67434,pi/4],  let y = pi/4 - x, then
         *              tan(x) = tan(pi/4-y) = (1-tan(y))/(1+tan(y))
         *                     = 1 - 2*(tan(y) - (tan(y)^2)/(1+tan(y)))
         */
        private static final double
            pio4  =  0x1.921fb54442d18p-1,  // 7.85398163397448278999e-01
            pio4lo=  0x1.1a62633145c07p-55; // 3.06161699786838301793e-17
        @Stable
        private static final double[]
            T = {
             0x1.5555555555563p-2,  //  3.33333333333334091986e-01
             0x1.111111110fe7ap-3,  //  1.33333333333201242699e-01
             0x1.ba1ba1bb341fep-5,  //  5.39682539762260521377e-02
             0x1.664f48406d637p-6,  //  2.18694882948595424599e-02
             0x1.226e3e96e8493p-7,  //  8.86323982359930005737e-03
             0x1.d6d22c9560328p-9,  //  3.59207910759131235356e-03
             0x1.7dbc8fee08315p-10, //  1.45620945432529025516e-03
             0x1.344d8f2f26501p-11, //  5.88041240820264096874e-04
             0x1.026f71a8d1068p-12, //  2.46463134818469906812e-04
             0x1.47e88a03792a6p-14, //  7.81794442939557092300e-05
             0x1.2b80f32f0a7e9p-14, //  7.14072491382608190305e-05
            -0x1.375cbdb605373p-16, // -1.85586374855275456654e-05
             0x1.b2a7074bf7ad4p-16, //  2.59073051863633712884e-05
        };

        static double __kernel_tan(double x, double y, int iy) {
            double z, r, v, w, s;
            int ix, hx;
            hx = __HI(x);   // high word of x
            ix = hx & EXP_SIGNIF_BITS;     // high word of |x|
            if (ix < 0x3e30_0000) {  // x < 2**-28
                if ((int)x == 0) {   // generate inexact
                    if (((ix | __LO(x)) | (iy + 1)) == 0) {
                        return 1.0 / Math.abs(x);
                    } else {
                        if (iy == 1) {
                            return x;
                        } else {    // compute -1 / (x+y) carefully
                            double a, t;

                            z = w = x + y;
                            z= __LO(z, 0);
                            v = y - (z - x);
                            t = a = -1.0 / w;
                            t = __LO(t, 0);
                            s = 1.0 + t * z;
                            return t + a * (s + t * v);
                        }
                    }
                }
            }
            if (ix >= 0x3FE5_9428) {  // |x| >= 0.6744
                if ( hx < 0) {
                    x = -x;
                    y = -y;
                }
                z = pio4 - x;
                w = pio4lo - y;
                x = z + w;
                y = 0.0;
            }
            z       =  x*x;
            w       =  z*z;
            /* Break x^5*(T[1]+x^2*T[2]+...) into
             *    x^5(T[1]+x^4*T[3]+...+x^20*T[11]) +
             *    x^5(x^2*(T[2]+x^4*T[4]+...+x^22*[T12]))
             */
            r =    T[1] + w*(T[3] + w*(T[5] + w*(T[7] + w*(T[9]  + w*T[11]))));
            v = z*(T[2] + w*(T[4] + w*(T[6] + w*(T[8] + w*(T[10] + w*T[12])))));
            s = z*x;
            r = y + z*(s*(r + v) + y);
            r += T[0]*s;
            w = x + r;
            if (ix >= 0x3FE5_9428) {
                v = (double)iy;
                return (double)(1-((hx >> 30) & 2))*(v - 2.0*(x - (w*w/(w + v) - r)));
            }
            if (iy == 1) {
                return w;
            } else { /* if were to allow error up to 2 ulp,
                        could simply return -1.0/(x + r) here */
                //  compute -1.0/(x + r) accurately
                double a,t;
                z = w;
                z = __LO(z, 0);
                v = r - (z - x);   // z + v = r + x
                t = a  = -1.0/w;    // a = -1.0/w
                t = __LO(t, 0);
                s = 1.0 + t*z;
                return t + a*(s + t*v);
            }
        }
    }

    /** __ieee754_rem_pio2(x,y)
     *
     * return the remainder of x rem pi/2 in y[0]+y[1]
     * use __kernel_rem_pio2()
     */
    static final class RemPio2 {
        /*
         * Table of constants for 2/pi, 396 Hex digits (476 decimal) of 2/pi
         */
        @Stable
        private static final int[] two_over_pi = {
            0xA2F983, 0x6E4E44, 0x1529FC, 0x2757D1, 0xF534DD, 0xC0DB62,
            0x95993C, 0x439041, 0xFE5163, 0xABDEBB, 0xC561B7, 0x246E3A,
            0x424DD2, 0xE00649, 0x2EEA09, 0xD1921C, 0xFE1DEB, 0x1CB129,
            0xA73EE8, 0x8235F5, 0x2EBB44, 0x84E99C, 0x7026B4, 0x5F7E41,
            0x3991D6, 0x398353, 0x39F49C, 0x845F8B, 0xBDF928, 0x3B1FF8,
            0x97FFDE, 0x05980F, 0xEF2F11, 0x8B5A0A, 0x6D1F6D, 0x367ECF,
            0x27CB09, 0xB74F46, 0x3F669E, 0x5FEA2D, 0x7527BA, 0xC7EBE5,
            0xF17B3D, 0x0739F7, 0x8A5292, 0xEA6BFB, 0x5FB11F, 0x8D5D08,
            0x560330, 0x46FC7B, 0x6BABF0, 0xCFBC20, 0x9AF436, 0x1DA9E3,
            0x91615E, 0xE61B08, 0x659985, 0x5F14A0, 0x68408D, 0xFFD880,
            0x4D7327, 0x310606, 0x1556CA, 0x73A8C9, 0x60E27B, 0xC08C6B,
        };

        @Stable
        private static final int[] npio2_hw = {
            0x3FF921FB, 0x400921FB, 0x4012D97C, 0x401921FB, 0x401F6A7A, 0x4022D97C,
            0x4025FDBB, 0x402921FB, 0x402C463A, 0x402F6A7A, 0x4031475C, 0x4032D97C,
            0x40346B9C, 0x4035FDBB, 0x40378FDB, 0x403921FB, 0x403AB41B, 0x403C463A,
            0x403DD85A, 0x403F6A7A, 0x40407E4C, 0x4041475C, 0x4042106C, 0x4042D97C,
            0x4043A28C, 0x40446B9C, 0x404534AC, 0x4045FDBB, 0x4046C6CB, 0x40478FDB,
            0x404858EB, 0x404921FB,
        };

        /*
         * invpio2:  53 bits of 2/pi
         * pio2_1:   first  33 bit of pi/2
         * pio2_1t:  pi/2 - pio2_1
         * pio2_2:   second 33 bit of pi/2
         * pio2_2t:  pi/2 - (pio2_1+pio2_2)
         * pio2_3:   third  33 bit of pi/2
         * pio2_3t:  pi/2 - (pio2_1+pio2_2+pio2_3)
         */

        private static final double
            invpio2 =  0x1.45f306dc9c883p-1,   // 6.36619772367581382433e-01
            pio2_1  =  0x1.921fb544p0,         // 1.57079632673412561417e+00
            pio2_1t =  0x1.0b4611a626331p-34,  // 6.07710050650619224932e-11
            pio2_2  =  0x1.0b4611a6p-34,       // 6.07710050630396597660e-11
            pio2_2t =  0x1.3198a2e037073p-69,  // 2.02226624879595063154e-21
            pio2_3  =  0x1.3198a2ep-69,        // 2.02226624871116645580e-21
            pio2_3t =  0x1.b839a252049c1p-104; // 8.47842766036889956997e-32

        static int __ieee754_rem_pio2(double x, double[] y) {
            double z = 0.0, w, t, r, fn;
            double[] tx = new double[3];
            int e0, i, j, nx, n, ix, hx;

            hx = __HI(x);           // high word of x
            ix = hx & EXP_SIGNIF_BITS;
            if (ix <= 0x3fe9_21fb) {   // |x| ~<= pi/4 , no need for reduction
                y[0] = x;
                y[1] = 0;
                return 0;
            }
            if (ix < 0x4002_d97c) {  // |x| < 3pi/4, special case with n=+-1
                if (hx > 0) {
                    z = x - pio2_1;
                    if (ix != 0x3ff9_21fb) {    // 33+53 bit pi is good enough
                        y[0] = z - pio2_1t;
                        y[1] = (z - y[0]) - pio2_1t;
                    } else {                // near pi/2, use 33+33+53 bit pi
                        z -= pio2_2;
                        y[0] = z - pio2_2t;
                        y[1] = (z-y[0])-pio2_2t;
                    }
                    return 1;
                } else {    // negative x
                    z = x + pio2_1;
                    if (ix != 0x3ff_921fb) {    // 33+53 bit pi is good enough
                        y[0] = z + pio2_1t;
                        y[1] = (z - y[0]) + pio2_1t;
                    } else {                // near pi/2, use 33+33+53 bit pi
                        z += pio2_2;
                        y[0] = z + pio2_2t;
                        y[1] = (z - y[0]) + pio2_2t;
                    }
                    return -1;
                }
            }
            if (ix <= 0x4139_21fb) { // |x| ~<= 2^19*(pi/2), medium size
                t  = Math.abs(x);
                n  = (int) (t*invpio2 + 0.5);
                fn = (double)n;
                r  = t - fn*pio2_1;
                w  = fn*pio2_1t;    // 1st round good to 85 bit
                if (n < 32 && ix != npio2_hw[n - 1]) {
                    y[0] = r - w;     // quick check no cancellation
                } else {
                    j = ix >> 20;
                    y[0] = r - w;
                    i = j - (((__HI(y[0])) >> 20) & 0x7ff);
                    if (i > 16) {  // 2nd iteration needed, good to 118
                        t  = r;
                        w  = fn*pio2_2;
                        r  = t - w;
                        w  = fn*pio2_2t - ((t - r) - w);
                        y[0] = r - w;
                        i = j - (((__HI(y[0])) >> 20) & 0x7ff);
                        if (i > 49)  { // 3rd iteration need, 151 bits acc
                            t  = r; // will cover all possible cases
                            w  = fn*pio2_3;
                            r  = t - w;
                            w  = fn*pio2_3t - ((t - r) - w);
                            y[0] = r - w;
                        }
                    }
                }
                y[1] = (r - y[0]) - w;
                if (hx < 0) {
                    y[0] = -y[0];
                    y[1] = -y[1];
                    return -n;
                } else {
                    return n;
                }
            }
            /*
             * all other (large) arguments
             */
            if (ix >= EXP_BITS) {            // x is inf or NaN
                y[0] = y[1] = x - x;
                return 0;
            }
            // set z = scalbn(|x|, ilogb(x)-23)
            z = __LO(z, __LO(x));
            e0 = (ix >> 20) - 1046;        // e0 = ilogb(z) - 23;
            z = __HI(z, ix - (e0 << 20));
            for (i=0; i < 2; i++) {
                tx[i] = (double)((int)(z));
                z     = (z - tx[i])*TWO24;
            }
            tx[2] = z;
            nx = 3;
            while (tx[nx - 1] == 0.0) { // skip zero term
                nx--;
            }
            n = KernelRemPio2.__kernel_rem_pio2(tx, y, e0, nx, 2, two_over_pi);
            if (hx < 0) {
                y[0] = -y[0];
                y[1] = -y[1];
                return -n;
            }
            return n;
        }
    }

    /**
     * __kernel_rem_pio2(x,y,e0,nx,prec,ipio2)
     * double x[],y[]; int e0,nx,prec; int ipio2[];
     *
     * __kernel_rem_pio2 return the last three digits of N with
     *              y = x - N*pi/2
     * so that |y| < pi/2.
     *
     * The method is to compute the integer (mod 8) and fraction parts of
     * (2/pi)*x without doing the full multiplication. In general we
     * skip the part of the product that are known to be a huge integer (
     * more accurately, = 0 mod 8 ). Thus the number of operations are
     * independent of the exponent of the input.
     *
     * (2/pi) is represented by an array of 24-bit integers in ipio2[].
     *
     * Input parameters:
     *      x[]     The input value (must be positive) is broken into nx
     *              pieces of 24-bit integers in double precision format.
     *              x[i] will be the i-th 24 bit of x. The scaled exponent
     *              of x[0] is given in input parameter e0 (i.e., x[0]*2^e0
     *              match x's up to 24 bits.
     *
     *              Example of breaking a double positive z into x[0]+x[1]+x[2]:
     *                      e0 = ilogb(z)-23
     *                      z  = scalbn(z,-e0)
     *              for i = 0,1,2
     *                      x[i] = floor(z)
     *                      z    = (z-x[i])*2**24
     *
     *
     *      y[]     output result in an array of double precision numbers.
     *              The dimension of y[] is:
     *                      24-bit  precision       1
     *                      53-bit  precision       2
     *                      64-bit  precision       2
     *                      113-bit precision       3
     *              The actual value is the sum of them. Thus for 113-bit
     *              precision, one may have to do something like:
     *
     *              long double t,w,r_head, r_tail;
     *              t = (long double)y[2] + (long double)y[1];
     *              w = (long double)y[0];
     *              r_head = t+w;
     *              r_tail = w - (r_head - t);
     *
     *      e0      The exponent of x[0]
     *
     *      nx      dimension of x[]
     *
     *      prec    an integer indicating the precision:
     *                      0       24  bits (single)
     *                      1       53  bits (double)
     *                      2       64  bits (extended)
     *                      3       113 bits (quad)
     *
     *      ipio2[]
     *              integer array, contains the (24*i)-th to (24*i+23)-th
     *              bit of 2/pi after binary point. The corresponding
     *              floating value is
     *
     *                      ipio2[i] * 2^(-24(i+1)).
     *
     * External function:
     *      double scalbn(), floor();
     *
     *
     * Here is the description of some local variables:
     *
     *      jk      jk+1 is the initial number of terms of ipio2[] needed
     *              in the computation. The recommended value is 2,3,4,
     *              6 for single, double, extended,and quad.
     *
     *      jz      local integer variable indicating the number of
     *              terms of ipio2[] used.
     *
     *      jx      nx - 1
     *
     *      jv      index for pointing to the suitable ipio2[] for the
     *              computation. In general, we want
     *                      ( 2^e0*x[0] * ipio2[jv-1]*2^(-24jv) )/8
     *              is an integer. Thus
     *                      e0-3-24*jv >= 0 or (e0-3)/24 >= jv
     *              Hence jv = max(0,(e0-3)/24).
     *
     *      jp      jp+1 is the number of terms in PIo2[] needed, jp = jk.
     *
     *      q[]     double array with integral value, representing the
     *              24-bits chunk of the product of x and 2/pi.
     *
     *      q0      the corresponding exponent of q[0]. Note that the
     *              exponent for q[i] would be q0-24*i.
     *
     *      PIo2[]  double precision array, obtained by cutting pi/2
     *              into 24 bits chunks.
     *
     *      f[]     ipio2[] in floating point
     *
     *      iq[]    integer array by breaking up q[] in 24-bits chunk.
     *
     *      fq[]    final product of x*(2/pi) in fq[0],..,fq[jk]
     *
     *      ih      integer. If >0 it indicates q[] is >= 0.5, hence
     *              it also indicates the *sign* of the result.
     *
     */
    static final class KernelRemPio2 {
        /*
         * Constants:
         * The hexadecimal values are the intended ones for the following
         * constants. The decimal values may be used, provided that the
         * compiler will convert from decimal to binary accurately enough
         * to produce the hexadecimal values shown.
         */

        @Stable
        private static final int init_jk[] = {2, 3, 4, 6}; // initial value for jk

        @Stable
        private static final double PIo2[] = {
            0x1.921fb4p0,    // 1.57079625129699707031e+00
            0x1.4442dp-24,   // 7.54978941586159635335e-08
            0x1.846988p-48,  // 5.39030252995776476554e-15
            0x1.8cc516p-72,  // 3.28200341580791294123e-22
            0x1.01b838p-96,  // 1.27065575308067607349e-29
            0x1.a25204p-120, // 1.22933308981111328932e-36
            0x1.382228p-145, // 2.73370053816464559624e-44
            0x1.9f31dp-169,  // 2.16741683877804819444e-51
        };

        static final double
            twon24  = 0x1.0p-24; // 5.96046447753906250000e-08

        static int __kernel_rem_pio2(double[] x, double[] y, int e0, int nx, int prec, final int[] ipio2) {
            int jz, jx, jv, jp, jk, carry, n, i, j, k, m, q0, ih;
            int[] iq = new int[20];
            double z,fw;
            double [] f = new double[20];
            double [] fq= new double[20];
            double [] q = new double[20];

            // initialize jk
            jk = init_jk[prec];
            jp = jk;

            // determine jx, jv, q0, note that 3 > q0
            jx = nx - 1;
            jv = (e0 - 3)/24;
            if (jv < 0) {
                jv = 0;
            }
            q0 =  e0 - 24*(jv + 1);

            // set up f[0] to f[jx+jk] where f[jx+jk] = ipio2[jv+jk]
            j = jv - jx;
            m = jx + jk;
            for (i = 0; i <= m; i++, j++) {
                f[i] = (j < 0) ? 0.0 : (double) ipio2[j];
            }

            // compute q[0],q[1],...q[jk]
            for (i=0; i <= jk; i++) {
                for(j = 0, fw = 0.0; j <= jx; j++) {
                    fw += x[j]*f[jx + i - j];
                }
                q[i] = fw;
            }

            jz = jk;
            while(true) {
                // distill q[] into iq[] reversingly
                for(i=0, j=jz, z=q[jz]; j > 0; i++, j--) {
                    fw    =  (double)((int)(twon24* z));
                    iq[i] =  (int)(z - TWO24*fw);
                    z     =  q[j - 1] + fw;
                }

                // compute n
                z  = Math.scalb(z, q0);              // actual value of z
                z -= 8.0*Math.floor(z*0.125);        // trim off integer >= 8
                n  = (int) z;
                z -= (double)n;
                ih = 0;
                if (q0 > 0) {      // need iq[jz - 1] to determine n
                    i  = (iq[jz - 1] >> (24 - q0));
                    n += i;
                    iq[jz - 1] -= i << (24 - q0);
                    ih = iq[jz - 1] >> (23 - q0);
                } else if (q0 == 0) {
                    ih = iq[jz-1]>>23;
                } else if (z >= 0.5) {
                    ih=2;
                }

                if (ih > 0) {      // q > 0.5
                    n += 1;
                    carry = 0;
                    for (i=0; i < jz; i++) {        // compute 1-q
                        j = iq[i];
                        if (carry == 0) {
                            if (j != 0) {
                                carry = 1;
                                iq[i] = 0x100_0000 - j;
                            }
                        } else {
                            iq[i] = 0xff_ffff - j;
                        }
                    }
                    if (q0 > 0) {          // rare case: chance is 1 in 12
                        switch(q0) {
                        case 1:
                            iq[jz-1] &= 0x7f_ffff;
                            break;
                        case 2:
                            iq[jz-1] &= 0x3f_ffff;
                            break;
                        }
                    }
                    if (ih == 2) {
                        z = 1.0 - z;
                        if (carry != 0) {
                            z -= Math.scalb(1.0, q0);
                        }
                    }
                }

                // check if recomputation is needed
                if (z == 0.0) {
                    j = 0;
                    for (i = jz - 1; i >= jk; i--) {
                        j |= iq[i];
                    }
                    if (j == 0) { // need recomputation
                        for(k=1; iq[jk - k] == 0; k++);   // k = no. of terms needed

                        for(i = jz + 1; i <= jz + k; i++) {   // add q[jz+1] to q[jz+k]
                            f[jx + i] = (double) ipio2[jv + i];
                            for (j=0, fw = 0.0; j <= jx; j++) {
                                fw += x[j]*f[jx + i - j];
                            }
                            q[i] = fw;
                        }
                        jz += k;
                        continue;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }

            // chop off zero terms
            if (z == 0.0) {
                jz -= 1;
                q0 -= 24;
                while (iq[jz] == 0) {
                    jz--;
                    q0-=24;
                }
            } else { // break z into 24-bit if necessary
                z = Math.scalb(z, -q0);
                if (z >= TWO24) {
                    fw = (double)((int)(twon24*z));
                    iq[jz] = (int)(z - TWO24*fw);
                    jz += 1;
                    q0 += 24;
                    iq[jz] = (int) fw;
                } else {
                    iq[jz] = (int) z;
                }
            }

            // convert integer "bit" chunk to floating-point value
            fw = Math.scalb(1.0, q0);
            for(i = jz; i>=0; i--) {
                q[i] = fw*(double)iq[i];
                fw *= twon24;
            }

            // compute PIo2[0,...,jp]*q[jz,...,0]
            for(i = jz; i>=0; i--) {
                for (fw = 0.0, k = 0; k <= jp && k <= jz-i; k++) {
                    fw += PIo2[k]*q[i + k];
                }
                fq[jz - i] = fw;
            }

            // compress fq[] into y[]
            switch(prec) {
            case 0:
                fw = 0.0;
                for (i = jz; i >=0; i--) {
                    fw += fq[i];
                }
                y[0] = (ih == 0)? fw: -fw;
                break;
            case 1:
            case 2:
                fw = 0.0;
                for (i = jz; i>=0; i--) {
                    fw += fq[i];
                }
                y[0] = (ih == 0) ? fw: -fw;
                fw = fq[0] - fw;
                for (i = 1; i <= jz; i++) {
                    fw += fq[i];
                }
                y[1] = (ih == 0)? fw: -fw;
                break;
            case 3:     // painful
                for (i = jz; i > 0; i--) {
                    fw      = fq[i - 1] + fq[i];
                    fq[i]  += fq[i - 1] - fw;
                    fq[i - 1] = fw;
                }
                for (i = jz; i>1; i--) {
                    fw      = fq[i - 1] + fq[i];
                    fq[i]  += fq[i - 1] - fw;
                    fq[i-1] = fw;
                }
                for (fw = 0.0, i = jz; i >= 2; i--) {
                    fw += fq[i];
                }
                if (ih == 0) {
                    y[0] =  fq[0];
                    y[1] =  fq[1];
                    y[2] =  fw;
                } else {
                    y[0] = -fq[0];
                    y[1] = -fq[1];
                    y[2] = -fw;
                }
            }
            return n & 7;
        }
    }

    /** Returns the arcsine of x.
     *
     * Method :
     *      Since  asin(x) = x + x^3/6 + x^5*3/40 + x^7*15/336 + ...
     *      we approximate asin(x) on [0,0.5] by
     *              asin(x) = x + x*x^2*R(x^2)
     *      where
     *              R(x^2) is a rational approximation of (asin(x)-x)/x^3
     *      and its remez error is bounded by
     *              |(asin(x)-x)/x^3 - R(x^2)| < 2^(-58.75)
     *
     *      For x in [0.5,1]
     *              asin(x) = pi/2-2*asin(sqrt((1-x)/2))
     *      Let y = (1-x), z = y/2, s := sqrt(z), and pio2_hi+pio2_lo=pi/2;
     *      then for x>0.98
     *              asin(x) = pi/2 - 2*(s+s*z*R(z))
     *                      = pio2_hi - (2*(s+s*z*R(z)) - pio2_lo)
     *      For x<=0.98, let pio4_hi = pio2_hi/2, then
     *              f = hi part of s;
     *              c = sqrt(z) - f = (z-f*f)/(s+f)         ...f+c=sqrt(z)
     *      and
     *              asin(x) = pi/2 - 2*(s+s*z*R(z))
     *                      = pio4_hi+(pio4-2s)-(2s*z*R(z)-pio2_lo)
     *                      = pio4_hi+(pio4-2f)-(2s*z*R(z)-(pio2_lo+2c))
     *
     * Special cases:
     *      if x is NaN, return x itself;
     *      if |x|>1, return NaN with invalid signal.
     *
     */
    static final class Asin {
        private Asin() {throw new UnsupportedOperationException();}

        private static final double
            pio2_hi = 0x1.921fb54442d18p0,   //  1.57079632679489655800e+00
            pio2_lo = 0x1.1a62633145c07p-54, //  6.12323399573676603587e-17
            pio4_hi = 0x1.921fb54442d18p-1,  //  7.85398163397448278999e-01
        // coefficient for R(x^2)
            pS0 =  0x1.5555555555555p-3,     //  1.66666666666666657415e-01
            pS1 = -0x1.4d61203eb6f7dp-2,     // -3.25565818622400915405e-01
            pS2 =  0x1.9c1550e884455p-3,     //  2.01212532134862925881e-01
            pS3 = -0x1.48228b5688f3bp-5,     // -4.00555345006794114027e-02
            pS4 =  0x1.9efe07501b288p-11,    //  7.91534994289814532176e-04
            pS5 =  0x1.23de10dfdf709p-15,    //  3.47933107596021167570e-05
            qS1 = -0x1.33a271c8a2d4bp1,      // -2.40339491173441421878e+00
            qS2 =  0x1.02ae59c598ac8p1,      //  2.02094576023350569471e+00
            qS3 = -0x1.6066c1b8d0159p-1,     // -6.88283971605453293030e-01
            qS4 =  0x1.3b8c5b12e9282p-4;     //  7.70381505559019352791e-02

        static double compute(double x) {
            double t = 0, w, p, q, c, r, s;
            int hx, ix;
            hx = __HI(x);
            ix = hx & EXP_SIGNIF_BITS;
            if (ix >= 0x3ff0_0000) {           // |x| >= 1
                if(((ix - 0x3ff0_0000) | __LO(x)) == 0) {
                    // asin(1) = +-pi/2 with inexact
                    return x*pio2_hi + x*pio2_lo;
                }
                return (x - x)/(x - x);         // asin(|x| > 1) is NaN
            } else if (ix < 0x3fe0_0000) {     // |x| < 0.5
                if (ix < 0x3e40_0000) {        // if |x| < 2**-27
                    if (HUGE + x > 1.0) {// return x with inexact if x != 0
                        return x;
                    }
                } else {
                    t = x*x;
                }
                p = t*(pS0 + t*(pS1 + t*(pS2 + t*(pS3 + t*(pS4 + t*pS5)))));
                q = 1.0 + t*(qS1 + t*(qS2 + t*(qS3 + t*qS4)));
                w = p/q;
                return x + x*w;
            }
            // 1 > |x| >= 0.5
            w = 1.0 - Math.abs(x);
            t = w*0.5;
            p = t*(pS0 + t*(pS1 + t*(pS2 + t*(pS3 + t*(pS4 + t*pS5)))));
            q = 1.0 + t*(qS1 + t*(qS2 + t*(qS3 + t*qS4)));
            s = Math.sqrt(t);
            if (ix >= 0x3FEF_3333) {    // if |x| > 0.975
                w = p/q;
                t = pio2_hi - (2.0*(s + s*w) - pio2_lo);
            } else {
                w  = s;
                w  = __LO(w, 0);
                c  = (t - w*w)/(s + w);
                r  = p/q;
                p  = 2.0*s*r - (pio2_lo - 2.0*c);
                q  = pio4_hi - 2.0*w;
                t  = pio4_hi - (p - q);
            }
            return (hx > 0) ? t : -t;
        }
    }

    /** Returns the arccosine of x.
     * Method :
     *      acos(x)  = pi/2 - asin(x)
     *      acos(-x) = pi/2 + asin(x)
     * For |x| <= 0.5
     *      acos(x) = pi/2 - (x + x*x^2*R(x^2))     (see asin.c)
     * For x > 0.5
     *      acos(x) = pi/2 - (pi/2 - 2asin(sqrt((1-x)/2)))
     *              = 2asin(sqrt((1-x)/2))
     *              = 2s + 2s*z*R(z)        ...z=(1-x)/2, s=sqrt(z)
     *              = 2f + (2c + 2s*z*R(z))
     *     where f=hi part of s, and c = (z-f*f)/(s+f) is the correction term
     *     for f so that f+c ~ sqrt(z).
     * For x <- 0.5
     *      acos(x) = pi - 2asin(sqrt((1-|x|)/2))
     *              = pi - 0.5*(s+s*z*R(z)), where z=(1-|x|)/2,s=sqrt(z)
     *
     * Special cases:
     *      if x is NaN, return x itself;
     *      if |x|>1, return NaN with invalid signal.
     *
     * Function needed: sqrt
     */
    static final class Acos {
        private Acos() {throw new UnsupportedOperationException();}

        private static final double
            pio2_hi =  0x1.921fb54442d18p0,   //  1.57079632679489655800e+00
            pio2_lo =  0x1.1a62633145c07p-54, //  6.12323399573676603587e-17
            pS0     =  0x1.5555555555555p-3,  //  1.66666666666666657415e-01
            pS1     = -0x1.4d61203eb6f7dp-2,  // -3.25565818622400915405e-01
            pS2     =  0x1.9c1550e884455p-3,  //  2.01212532134862925881e-01
            pS3     = -0x1.48228b5688f3bp-5,  // -4.00555345006794114027e-02
            pS4     =  0x1.9efe07501b288p-11, //  7.91534994289814532176e-04
            pS5     =  0x1.23de10dfdf709p-15, //  3.47933107596021167570e-05
            qS1     = -0x1.33a271c8a2d4bp1,   // -2.40339491173441421878e+00
            qS2     =  0x1.02ae59c598ac8p1,   //  2.02094576023350569471e+00
            qS3     = -0x1.6066c1b8d0159p-1,  // -6.88283971605453293030e-01
            qS4     =  0x1.3b8c5b12e9282p-4;  //  7.70381505559019352791e-02

        static double compute(double x) {
            double z, p, q, r, w, s, c, df;
            int hx, ix;
            hx = __HI(x);
            ix = hx & EXP_SIGNIF_BITS;
            if (ix >= 0x3ff0_0000) {    // |x| >= 1
                if (((ix - 0x3ff0_0000) | __LO(x)) == 0) {  // |x| == 1
                    if (hx > 0) {// acos(1) = 0
                        return 0.0;
                    }else {       // acos(-1)= pi
                        return Math.PI + 2.0*pio2_lo;
                    }
                }
                return (x - x)/(x - x);         // acos(|x| > 1) is NaN
            }
            if (ix < 0x3fe0_0000) {     // |x| < 0.5
                if (ix <= 0x3c60_0000) {  // if |x| < 2**-57
                    return pio2_hi + pio2_lo;
                }
                z = x*x;
                p = z*(pS0 + z*(pS1 + z*(pS2 + z*(pS3 + z*(pS4 + z*pS5)))));
                q = 1.0 + z*(qS1 + z*(qS2 + z*(qS3 + z*qS4)));
                r = p/q;
                return pio2_hi - (x - (pio2_lo - x*r));
            } else if (hx < 0) {             // x < -0.5
                z = (1.0 + x)*0.5;
                p = z*(pS0 + z*(pS1 + z*(pS2 + z*(pS3 + z*(pS4 + z*pS5)))));
                q = 1.0 + z*(qS1 + z*(qS2 + z*(qS3 + z*qS4)));
                s = Math.sqrt(z);
                r = p/q;
                w = r*s - pio2_lo;
                return Math.PI - 2.0*(s+w);
            } else {                        // x > 0.5
                z = (1.0 - x)*0.5;
                s = Math.sqrt(z);
                df = s;
                df = __LO(df, 0);
                c  = (z - df*df)/(s + df);
                p = z*(pS0 + z*(pS1 + z*(pS2 + z*(pS3 + z*(pS4 + z*pS5)))));
                q = 1.0 + z*(qS1 + z*(qS2 + z*(qS3 + z*qS4)));
                r = p/q;
                w = r*s + c;
                return 2.0*(df + w);
            }
        }
    }

    /* Returns the arctangent of x.
     * Method
     *   1. Reduce x to positive by atan(x) = -atan(-x).
     *   2. According to the integer k=4t+0.25 chopped, t=x, the argument
     *      is further reduced to one of the following intervals and the
     *      arctangent of t is evaluated by the corresponding formula:
     *
     *      [0,7/16]      atan(x) = t-t^3*(a1+t^2*(a2+...(a10+t^2*a11)...)
     *      [7/16,11/16]  atan(x) = atan(1/2) + atan( (t-0.5)/(1+t/2) )
     *      [11/16.19/16] atan(x) = atan( 1 ) + atan( (t-1)/(1+t) )
     *      [19/16,39/16] atan(x) = atan(3/2) + atan( (t-1.5)/(1+1.5t) )
     *      [39/16,INF]   atan(x) = atan(INF) + atan( -1/t )
     *
     * Constants:
     * The hexadecimal values are the intended ones for the following
     * constants. The decimal values may be used, provided that the
     * compiler will convert from decimal to binary accurately enough
     * to produce the hexadecimal values shown.
     */
    static final class Atan {
        private Atan() {throw new UnsupportedOperationException();}

        @Stable
        private static final double atanhi[] = {
            0x1.dac670561bb4fp-2,  // atan(0.5)hi 4.63647609000806093515e-01
            0x1.921fb54442d18p-1,  // atan(1.0)hi 7.85398163397448278999e-01
            0x1.f730bd281f69bp-1,  // atan(1.5)hi 9.82793723247329054082e-01
            0x1.921fb54442d18p0,   // atan(inf)hi 1.57079632679489655800e+00
        };

        @Stable
        private static final double atanlo[] = {
            0x1.a2b7f222f65e2p-56, // atan(0.5)lo 2.26987774529616870924e-17
            0x1.1a62633145c07p-55, // atan(1.0)lo 3.06161699786838301793e-17
            0x1.007887af0cbbdp-56, // atan(1.5)lo 1.39033110312309984516e-17
            0x1.1a62633145c07p-54, // atan(inf)lo 6.12323399573676603587e-17
        };

        @Stable
        private static final double aT[] = {
             0x1.555555555550dp-2, //  3.33333333333329318027e-01
            -0x1.999999998ebc4p-3, // -1.99999999998764832476e-01
             0x1.24924920083ffp-3, //  1.42857142725034663711e-01
            -0x1.c71c6fe231671p-4, // -1.11111104054623557880e-01
             0x1.745cdc54c206ep-4, //  9.09088713343650656196e-02
            -0x1.3b0f2af749a6dp-4, // -7.69187620504482999495e-02
             0x1.10d66a0d03d51p-4, //  6.66107313738753120669e-02
            -0x1.dde2d52defd9ap-5, // -5.83357013379057348645e-02
             0x1.97b4b24760debp-5, //  4.97687799461593236017e-02
            -0x1.2b4442c6a6c2fp-5, // -3.65315727442169155270e-02
             0x1.0ad3ae322da11p-6, //  1.62858201153657823623e-02
        };

        static double compute(double x) {
            double w, s1, s2, z;
            int ix, hx, id;

            hx = __HI(x);
            ix = hx & EXP_SIGNIF_BITS;
            if (ix >= 0x4410_0000) {    // if |x| >= 2^66
                if (ix > EXP_BITS ||
                    (ix == EXP_BITS && (__LO(x) != 0))) {
                    return x+x;             // NaN
                }
                if (hx > 0) {
                    return atanhi[3] + atanlo[3];
                } else {
                    return -atanhi[3] - atanlo[3];
                }
            } if (ix < 0x3fdc_0000) {        // |x| < 0.4375
                if (ix < 0x3e20_0000) {      // |x| < 2^-29
                    if (HUGE + x > 1.0) { // raise inexact
                        return x;
                    }
                }
                id = -1;
            } else {
                x = Math.abs(x);
                if (ix < 0x3ff3_0000) {         // |x| < 1.1875
                    if (ix < 0x3fe60000) {      // 7/16 <= |x| < 11/16
                        id = 0;
                        x = (2.0*x - 1.0)/(2.0 + x);
                    } else {                    // 11/16 <= |x| < 19/16
                        id = 1;
                        x  = (x - 1.0)/(x + 1.0);
                    }
                } else {
                    if (ix < 0x4003_8000) {      // |x| < 2.4375
                        id = 2;
                        x  = (x - 1.5)/(1.0 + 1.5*x);
                    } else {                    // 2.4375 <= |x| < 2^66
                        id = 3;
                        x  = -1.0/x;
                    }
                }
            }
            // end of argument reduction
            z = x*x;
            w = z*z;
            // break sum from i=0 to 10 aT[i]z**(i+1) into odd and even poly
            s1 = z*(aT[0] + w*(aT[2] + w*(aT[4] + w*(aT[6] + w*(aT[8] + w*aT[10])))));
            s2 = w*(aT[1] + w*(aT[3] + w*(aT[5] + w*(aT[7] + w*aT[9]))));
            if (id < 0) {
                return x - x*(s1 + s2);
            } else {
                z = atanhi[id] - ((x*(s1+s2) - atanlo[id]) - x);
                return (hx < 0) ? -z: z;
            }
        }
    }

    /**
     * Returns the angle theta from the conversion of rectangular
     * coordinates (x, y) to polar coordinates (r, theta).
     *
     * Method :
     *      1. Reduce y to positive by atan2(y,x)=-atan2(-y,x).
     *      2. Reduce x to positive by (if x and y are unexceptional):
     *              ARG (x+iy) = arctan(y/x)           ... if x > 0,
     *              ARG (x+iy) = pi - arctan[y/(-x)]   ... if x < 0,
     *
     * Special cases:
     *
     *      ATAN2((anything), NaN ) is NaN;
     *      ATAN2(NAN , (anything) ) is NaN;
     *      ATAN2(+-0, +(anything but NaN)) is +-0  ;
     *      ATAN2(+-0, -(anything but NaN)) is +-pi ;
     *      ATAN2(+-(anything but 0 and NaN), 0) is +-pi/2;
     *      ATAN2(+-(anything but INF and NaN), +INF) is +-0 ;
     *      ATAN2(+-(anything but INF and NaN), -INF) is +-pi;
     *      ATAN2(+-INF,+INF ) is +-pi/4 ;
     *      ATAN2(+-INF,-INF ) is +-3pi/4;
     *      ATAN2(+-INF, (anything but,0,NaN, and INF)) is +-pi/2;
     *
     * Constants:
     * The hexadecimal values are the intended ones for the following
     * constants. The decimal values may be used, provided that the
     * compiler will convert from decimal to binary accurately enough
     * to produce the hexadecimal values shown.
     */
    static final class Atan2 {
        private Atan2() {throw new UnsupportedOperationException();}

        private static final double
            tiny    = 1.0e-300,
            pi_o_4  = 0x1.921fb54442d18p-1,  // 7.8539816339744827900E-01
            pi_o_2  = 0x1.921fb54442d18p0,   // 1.5707963267948965580E+00
            pi_lo   = 0x1.1a62633145c07p-53; // 1.2246467991473531772E-16

        static double compute(double y, double x) {
            double z;
            int k, m, hx, hy, ix, iy;
            /*unsigned*/ int lx, ly;

            hx = __HI(x);
            ix = hx & EXP_SIGNIF_BITS;
            lx = __LO(x);
            hy = __HI(y);
            iy = hy & EXP_SIGNIF_BITS;
            ly = __LO(y);
            if (Double.isNaN(x) || Double.isNaN(y))
                return x + y;
            if (((hx - 0x3ff0_0000) | lx) == 0) // x = 1.0
                return StrictMath.atan(y);
            m = ((hy >> 31) & 1)|((hx >> 30) & 2);  // 2*sign(x) + sign(y)

            // when y = 0
            if ((iy | ly) == 0) {
                switch(m) {
                case 0:
                case 1: return y;               // atan(+/-0, +anything)  = +/-0
                case 2: return  Math.PI + tiny; // atan(+0,   -anything)  =  pi
                case 3: return -Math.PI - tiny; // atan(-0,   -anything)  = -pi
                }
            }
            // when x = 0
            if ((ix | lx) == 0) {
                return (hy < 0)?  -pi_o_2 - tiny : pi_o_2 + tiny;
            }

            // when x is INF
            if (ix == EXP_BITS) {
                if (iy == EXP_BITS) {
                    switch(m) {
                    case 0: return  pi_o_4 + tiny;      // atan(+INF, +INF)
                    case 1: return -pi_o_4 - tiny;      // atan(-INF, +INF)
                    case 2: return  3.0*pi_o_4 + tiny;  // atan(+INF, -INF)
                    case 3: return -3.0*pi_o_4 - tiny;  // atan(-INF, -INF)
                    }
                } else {
                    switch(m) {
                    case 0: return  0.0;                // atan(+..., +INF)
                    case 1: return -0.0;                // atan(-..., +INF)
                    case 2: return  Math.PI + tiny;     // atan(+..., -INF)
                    case 3: return -Math.PI - tiny;     // atan(-..., -INF)
                    }
                }
            }
            // when y is INF
            if (iy == EXP_BITS) {
                return (hy < 0)? -pi_o_2 - tiny : pi_o_2 + tiny;
            }

            // compute y/x
            k = (iy - ix) >> 20;
            if (k > 60) {   // |y/x| >  2**60
                z = pi_o_2+0.5*pi_lo;
            } else if (hx < 0 && k < -60) { // |y|/x < -2**60
                z = 0.0;
            } else { // safe to do y/x
                z = StrictMath.atan(Math.abs(y/x));
            }
            switch (m) {
            case 0:  return  z;                     // atan(+, +)
            case 1:  return -z;                     // atan(-, +)
            case 2:  return  Math.PI - (z - pi_lo); // atan(+, -)
            default: return (z - pi_lo) - Math.PI;  // atan(-, -), case 3
            }
        }
    }

    /**
     * Return correctly rounded sqrt.
     *           ------------------------------------------
     *           |  Use the hardware sqrt if you have one |
     *           ------------------------------------------
     * Method:
     *   Bit by bit method using integer arithmetic. (Slow, but portable)
     *   1. Normalization
     *      Scale x to y in [1,4) with even powers of 2:
     *      find an integer k such that  1 <= (y=x*2^(2k)) < 4, then
     *              sqrt(x) = 2^k * sqrt(y)
     *   2. Bit by bit computation
     *      Let q  = sqrt(y) truncated to i bit after binary point (q = 1),
     *           i                                                   0
     *                                     i+1         2
     *          s  = 2*q , and      y  =  2   * ( y - q  ).         (1)
     *           i      i            i                 i
     *
     *      To compute q    from q , one checks whether
     *                  i+1       i
     *
     *                            -(i+1) 2
     *                      (q + 2      ) <= y.                     (2)
     *                        i
     *                                                            -(i+1)
     *      If (2) is false, then q   = q ; otherwise q   = q  + 2      .
     *                             i+1   i             i+1   i
     *
     *      With some algebraic manipulation, it is not difficult to see
     *      that (2) is equivalent to
     *                             -(i+1)
     *                      s  +  2       <= y                      (3)
     *                       i                i
     *
     *      The advantage of (3) is that s  and y  can be computed by
     *                                    i      i
     *      the following recurrence formula:
     *          if (3) is false
     *
     *          s     =  s  ,       y    = y   ;                    (4)
     *           i+1      i          i+1    i
     *
     *          otherwise,
     *                         -i                     -(i+1)
     *          s     =  s  + 2  ,  y    = y  -  s  - 2             (5)
     *           i+1      i          i+1    i     i
     *
     *      One may easily use induction to prove (4) and (5).
     *      Note. Since the left hand side of (3) contain only i+2 bits,
     *            it does not necessary to do a full (53-bit) comparison
     *            in (3).
     *   3. Final rounding
     *      After generating the 53 bits result, we compute one more bit.
     *      Together with the remainder, we can decide whether the
     *      result is exact, bigger than 1/2ulp, or less than 1/2ulp
     *      (it will never equal to 1/2ulp).
     *      The rounding mode can be detected by checking whether
     *      huge + tiny is equal to huge, and whether huge - tiny is
     *      equal to huge for some floating point number "huge" and "tiny".
     *
     * Special cases:
     *      sqrt(+-0) = +-0         ... exact
     *      sqrt(inf) = inf
     *      sqrt(-ve) = NaN         ... with invalid signal
     *      sqrt(NaN) = NaN         ... with invalid signal for signaling NaN
     *
     * Other methods : see the appended file at the end of the program below.
     *---------------
     */
    static final class Sqrt {
        private Sqrt() {throw new UnsupportedOperationException();}

        private static final double tiny = 1.0e-300;

        static double compute(double x) {
            double z = 0.0;
            int sign = SIGN_BIT;
            /*unsigned*/ int r, t1, s1, ix1, q1;
            int ix0, s0, q, m, t, i;

            ix0 = __HI(x);  // high word of x
            ix1 = __LO(x);  // low word of x

            // take care of Inf and NaN
            if ((ix0 & EXP_BITS) == EXP_BITS) {
                return x*x + x; // sqrt(NaN)=NaN, sqrt(+inf)=+inf, sqrt(-inf)=sNaN
            }
            // take care of zero
            if (ix0 <= 0) {
                if (((ix0 & (~sign)) | ix1) == 0)
                    return x; // sqrt(+-0) = +-0
                else if (ix0 < 0)
                    return (x - x)/(x - x); // sqrt(-ve) = sNaN
            }
            // normalize x
            m = (ix0 >> 20);
            if (m == 0) { // subnormal x
                while (ix0 == 0) {
                    m -= 21;
                    ix0 |= (ix1 >>> 11); // unsigned shift
                    ix1 <<= 21;
                }
                for(i = 0; (ix0 & 0x0010_0000) == 0; i++) {
                    ix0 <<= 1;
                }
                m -= i-1;
                ix0 |= (ix1 >>> (32 - i)); // unsigned shift
                ix1 <<= i;
            }
            m -= 1023;      // unbias exponent */
            ix0 = (ix0 & 0x000f_ffff) | 0x0010_0000;
            if ((m & 1) != 0){        // odd m, double x to make it even
                ix0 += ix0 + ((ix1 & sign) >>> 31); // unsigned shift
                ix1 += ix1;
            }
            m >>= 1;        // m = [m/2]

            // generate sqrt(x) bit by bit
            ix0 += ix0 + ((ix1 & sign) >>> 31); // unsigned shift
            ix1 += ix1;
            q = q1 = s0 = s1 = 0;   // [q,q1] = sqrt(x)
            r = 0x0020_0000;        // r = moving bit from right to left

            while (r != 0) {
                t = s0 + r;
                if (t <= ix0) {
                    s0   = t + r;
                    ix0 -= t;
                    q   += r;
                }
                ix0 += ix0 + ((ix1 & sign) >>> 31); // unsigned shift
                ix1 += ix1;
                r >>>= 1; // unsigned shift
            }

            r = sign;
            while (r != 0) {
                t1 = s1 + r;
                t  = s0;
                if ((t < ix0) ||
                    ((t == ix0) && (Integer.compareUnsigned(t1, ix1) <= 0 ))) { // t1 <= ix1
                    s1 = t1 + r;
                    if (((t1 & sign) == sign) && (s1 & sign) == 0) {
                        s0 += 1;
                    }
                    ix0 -= t;
                    if (Integer.compareUnsigned(ix1, t1) < 0) {  // ix1 < t1
                        ix0 -= 1;
                    }
                    ix1 -= t1;
                    q1  += r;
                }
                ix0 += ix0 + ((ix1 & sign) >>> 31); // unsigned shift
                ix1 += ix1;
                r >>>= 1; // unsigned shift
            }

            // use floating add to find out rounding direction
            if ((ix0 | ix1) != 0) {
                z = 1.0 - tiny; // trigger inexact flag
                if (z >= 1.0) {
                    z = 1.0 + tiny;
                    if (q1 == 0xffff_ffff) {
                        q1 = 0;
                        q += 1;
                    } else if (z > 1.0) {
                        if (q1 == 0xffff_fffe) {
                            q += 1;
                        }
                        q1 += 2;
                    } else {
                        q1 += (q1 & 1);
                    }
                }
            }
            ix0 = (q >> 1) + 0x3fe0_0000;
            ix1 =  q1 >>> 1; // unsigned shift
            if ((q & 1) == 1) {
                ix1 |= sign;
            }
            ix0 += (m << 20);
            return __HI_LO(ix0, ix1);
        }
    }

    // The following comment is supplementary information from the FDLIBM sources.

    /*
     * Other methods  (use floating-point arithmetic)
     * -------------
     * (This is a copy of a drafted paper by Prof W. Kahan
     * and K.C. Ng, written in May, 1986)
     *
     *        Two algorithms are given here to implement sqrt(x)
     *        (IEEE double precision arithmetic) in software.
     *        Both supply sqrt(x) correctly rounded. The first algorithm (in
     *        Section A) uses newton iterations and involves four divisions.
     *        The second one uses reciproot iterations to avoid division, but
     *        requires more multiplications. Both algorithms need the ability
     *        to chop results of arithmetic operations instead of round them,
     *        and the INEXACT flag to indicate when an arithmetic operation
     *        is executed exactly with no roundoff error, all part of the
     *        standard (IEEE 754-1985). The ability to perform shift, add,
     *        subtract and logical AND operations upon 32-bit words is needed
     *        too, though not part of the standard.
     *
     * A.  sqrt(x) by Newton Iteration
     *
     *   (1)  Initial approximation
     *
     *        Let x0 and x1 be the leading and the trailing 32-bit words of
     *        a floating point number x (in IEEE double format) respectively
     *
     *            1    11                  52                           ...widths
     *           ------------------------------------------------------
     *        x: |s|    e     |             f                         |
     *           ------------------------------------------------------
     *              msb    lsb  msb                                 lsb ...order
     *
     *
     *             ------------------------        ------------------------
     *        x0:  |s|   e    |    f1     |    x1: |          f2           |
     *             ------------------------        ------------------------
     *
     *        By performing shifts and subtracts on x0 and x1 (both regarded
     *        as integers), we obtain an 8-bit approximation of sqrt(x) as
     *        follows.
     *
     *                k  := (x0>>1) + 0x1ff80000;
     *                y0 := k - T1[31&(k>>15)].       ... y ~ sqrt(x) to 8 bits
     *        Here k is a 32-bit integer and T1[] is an integer array containing
     *        correction terms. Now magically the floating value of y (y's
     *        leading 32-bit word is y0, the value of its trailing word is 0)
     *        approximates sqrt(x) to almost 8-bit.
     *
     *        Value of T1:
     *        static int T1[32]= {
     *        0,      1024,   3062,   5746,   9193,   13348,  18162,  23592,
     *        29598,  36145,  43202,  50740,  58733,  67158,  75992,  85215,
     *        83599,  71378,  60428,  50647,  41945,  34246,  27478,  21581,
     *        16499,  12183,  8588,   5674,   3403,   1742,   661,    130,};
     *
     *    (2) Iterative refinement
     *
     *        Apply Heron's rule three times to y, we have y approximates
     *        sqrt(x) to within 1 ulp (Unit in the Last Place):
     *
     *                y := (y+x/y)/2          ... almost 17 sig. bits
     *                y := (y+x/y)/2          ... almost 35 sig. bits
     *                y := y-(y-x/y)/2        ... within 1 ulp
     *
     *
     *        Remark 1.
     *            Another way to improve y to within 1 ulp is:
     *
     *                y := (y+x/y)            ... almost 17 sig. bits to 2*sqrt(x)
     *                y := y - 0x00100006     ... almost 18 sig. bits to sqrt(x)
     *
     *                                2
     *                            (x-y )*y
     *                y := y + 2* ----------  ...within 1 ulp
     *                               2
     *                             3y  + x
     *
     *
     *        This formula has one division fewer than the one above; however,
     *        it requires more multiplications and additions. Also x must be
     *        scaled in advance to avoid spurious overflow in evaluating the
     *        expression 3y*y+x. Hence it is not recommended unless division
     *        is slow. If division is very slow, then one should use the
     *        reciproot algorithm given in section B.
     *
     *    (3) Final adjustment
     *
     *        By twiddling y's last bit it is possible to force y to be
     *        correctly rounded according to the prevailing rounding mode
     *        as follows. Let r and i be copies of the rounding mode and
     *        inexact flag before entering the square root program. Also we
     *        use the expression y+-ulp for the next representable floating
     *        numbers (up and down) of y. Note that y+-ulp = either fixed
     *        point y+-1, or multiply y by nextafter(1,+-inf) in chopped
     *        mode.
     *
     *                I := FALSE;     ... reset INEXACT flag I
     *                R := RZ;        ... set rounding mode to round-toward-zero
     *                z := x/y;       ... chopped quotient, possibly inexact
     *                If(not I) then {        ... if the quotient is exact
     *                    if(z=y) {
     *                        I := i;  ... restore inexact flag
     *                        R := r;  ... restore rounded mode
     *                        return sqrt(x):=y.
     *                    } else {
     *                        z := z - ulp;   ... special rounding
     *                    }
     *                }
     *                i := TRUE;              ... sqrt(x) is inexact
     *                If (r=RN) then z=z+ulp  ... rounded-to-nearest
     *                If (r=RP) then {        ... round-toward-+inf
     *                    y = y+ulp; z=z+ulp;
     *                }
     *                y := y+z;               ... chopped sum
     *                y0:=y0-0x00100000;      ... y := y/2 is correctly rounded.
     *                I := i;                 ... restore inexact flag
     *                R := r;                 ... restore rounded mode
     *                return sqrt(x):=y.
     *
     *    (4) Special cases
     *
     *        Square root of +inf, +-0, or NaN is itself;
     *        Square root of a negative number is NaN with invalid signal.
     *
     *
     * B.  sqrt(x) by Reciproot Iteration
     *
     *   (1)  Initial approximation
     *
     *        Let x0 and x1 be the leading and the trailing 32-bit words of
     *        a floating point number x (in IEEE double format) respectively
     *        (see section A). By performing shifs and subtracts on x0 and y0,
     *        we obtain a 7.8-bit approximation of 1/sqrt(x) as follows.
     *
     *            k := 0x5fe80000 - (x0>>1);
     *            y0:= k - T2[63&(k>>14)].    ... y ~ 1/sqrt(x) to 7.8 bits
     *
     *        Here k is a 32-bit integer and T2[] is an integer array
     *        containing correction terms. Now magically the floating
     *        value of y (y's leading 32-bit word is y0, the value of
     *        its trailing word y1 is set to zero) approximates 1/sqrt(x)
     *        to almost 7.8-bit.
     *
     *        Value of T2:
     *        static int T2[64]= {
     *        0x1500, 0x2ef8, 0x4d67, 0x6b02, 0x87be, 0xa395, 0xbe7a, 0xd866,
     *        0xf14a, 0x1091b,0x11fcd,0x13552,0x14999,0x15c98,0x16e34,0x17e5f,
     *        0x18d03,0x19a01,0x1a545,0x1ae8a,0x1b5c4,0x1bb01,0x1bfde,0x1c28d,
     *        0x1c2de,0x1c0db,0x1ba73,0x1b11c,0x1a4b5,0x1953d,0x18266,0x16be0,
     *        0x1683e,0x179d8,0x18a4d,0x19992,0x1a789,0x1b445,0x1bf61,0x1c989,
     *        0x1d16d,0x1d77b,0x1dddf,0x1e2ad,0x1e5bf,0x1e6e8,0x1e654,0x1e3cd,
     *        0x1df2a,0x1d635,0x1cb16,0x1be2c,0x1ae4e,0x19bde,0x1868e,0x16e2e,
     *        0x1527f,0x1334a,0x11051,0xe951, 0xbe01, 0x8e0d, 0x5924, 0x1edd,};
     *
     *    (2) Iterative refinement
     *
     *        Apply Reciproot iteration three times to y and multiply the
     *        result by x to get an approximation z that matches sqrt(x)
     *        to about 1 ulp. To be exact, we will have
     *                -1ulp < sqrt(x)-z<1.0625ulp.
     *
     *        ... set rounding mode to Round-to-nearest
     *           y := y*(1.5-0.5*x*y*y)       ... almost 15 sig. bits to 1/sqrt(x)
     *           y := y*((1.5-2^-30)+0.5*x*y*y)... about 29 sig. bits to 1/sqrt(x)
     *        ... special arrangement for better accuracy
     *           z := x*y                     ... 29 bits to sqrt(x), with z*y<1
     *           z := z + 0.5*z*(1-z*y)       ... about 1 ulp to sqrt(x)
     *
     *        Remark 2. The constant 1.5-2^-30 is chosen to bias the error so that
     *        (a) the term z*y in the final iteration is always less than 1;
     *        (b) the error in the final result is biased upward so that
     *                -1 ulp < sqrt(x) - z < 1.0625 ulp
     *            instead of |sqrt(x)-z|<1.03125ulp.
     *
     *    (3) Final adjustment
     *
     *        By twiddling y's last bit it is possible to force y to be
     *        correctly rounded according to the prevailing rounding mode
     *        as follows. Let r and i be copies of the rounding mode and
     *        inexact flag before entering the square root program. Also we
     *        use the expression y+-ulp for the next representable floating
     *        numbers (up and down) of y. Note that y+-ulp = either fixed
     *        point y+-1, or multiply y by nextafter(1,+-inf) in chopped
     *        mode.
     *
     *        R := RZ;                ... set rounding mode to round-toward-zero
     *        switch(r) {
     *            case RN:            ... round-to-nearest
     *               if(x<= z*(z-ulp)...chopped) z = z - ulp; else
     *               if(x<= z*(z+ulp)...chopped) z = z; else z = z+ulp;
     *               break;
     *            case RZ:case RM:    ... round-to-zero or round-to--inf
     *               R:=RP;           ... reset rounding mod to round-to-+inf
     *               if(x<z*z ... rounded up) z = z - ulp; else
     *               if(x>=(z+ulp)*(z+ulp) ...rounded up) z = z+ulp;
     *               break;
     *            case RP:            ... round-to-+inf
     *               if(x>(z+ulp)*(z+ulp)...chopped) z = z+2*ulp; else
     *               if(x>z*z ...chopped) z = z+ulp;
     *               break;
     *        }
     *
     *        Remark 3. The above comparisons can be done in fixed point. For
     *        example, to compare x and w=z*z chopped, it suffices to compare
     *        x1 and w1 (the trailing parts of x and w), regarding them as
     *        two's complement integers.
     *
     *        ...Is z an exact square root?
     *        To determine whether z is an exact square root of x, let z1 be the
     *        trailing part of z, and also let x0 and x1 be the leading and
     *        trailing parts of x.
     *
     *        If ((z1&0x03ffffff)!=0) ... not exact if trailing 26 bits of z!=0
     *            I := 1;             ... Raise Inexact flag: z is not exact
     *        else {
     *            j := 1 - [(x0>>20)&1]       ... j = logb(x) mod 2
     *            k := z1 >> 26;              ... get z's 25-th and 26-th
     *                                            fraction bits
     *            I := i or (k&j) or ((k&(j+j+1))!=(x1&3));
     *        }
     *        R:= r           ... restore rounded mode
     *        return sqrt(x):=z.
     *
     *        If multiplication is cheaper then the foregoing red tape, the
     *        Inexact flag can be evaluated by
     *
     *            I := i;
     *            I := (z*z!=x) or I.
     *
     *        Note that z*z can overwrite I; this value must be sensed if it is
     *        True.
     *
     *        Remark 4. If z*z = x exactly, then bit 25 to bit 0 of z1 must be
     *        zero.
     *
     *                    --------------------
     *                z1: |        f2        |
     *                    --------------------
     *                bit 31             bit 0
     *
     *        Further more, bit 27 and 26 of z1, bit 0 and 1 of x1, and the odd
     *        or even of logb(x) have the following relations:
     *
     *        -------------------------------------------------
     *        bit 27,26 of z1         bit 1,0 of x1   logb(x)
     *        -------------------------------------------------
     *        00                      00              odd and even
     *        01                      01              even
     *        10                      10              odd
     *        10                      00              even
     *        11                      01              even
     *        -------------------------------------------------
     *
     *    (4) Special cases (see (4) of Section A).
     */

    /**
     * cbrt(x)
     * Return cube root of x
     */
    static final class Cbrt {
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
    static final class Hypot {
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
    static final class Pow {
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
            int ix = hx & EXP_SIGNIF_BITS;

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
                return (x - x)/(x - x);

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

                final double DP_H    = 0x1.2b80_34p-1;           // 5.84962487220764160156e-01
                final double DP_L    = 0x1.cfde_b43c_fd006p-27;  // 1.35003920212974897128e-08

                // Poly coefs for (3/2)*(log(x)-2s-2/3*s**3
                final double L1      =  0x1.3333_3333_33303p-1;  //  5.99999999999994648725e-01
                final double L2      =  0x1.b6db_6db6_fabffp-2;  //  4.28571428578550184252e-01
                final double L3      =  0x1.5555_5518_f264dp-2;  //  3.33333329818377432918e-01
                final double L4      =  0x1.1746_0a91_d4101p-2;  //  2.72728123808534006489e-01
                final double L5      =  0x1.d864_a93c_9db65p-3;  //  2.30660745775561754067e-01
                final double L6      =  0x1.a7e2_84a4_54eefp-3;  //  2.06975017800338417784e-01

                double BP_k = 1.0 + 0.5*k; // BP[0]=1.0, BP[1]=1.5
                u = x_abs - BP_k;
                v = 1.0 / (x_abs + BP_k);
                ss = u * v;
                s_h = ss;
                s_h = __LO(s_h, 0);
                // t_h=x_abs + BP[k] High
                t_h = 0.0;
                t_h = __HI(t_h, ((ix >> 1) | 0x20000000) + 0x00080000 + (k << 18) );
                t_l = x_abs - (t_h - BP_k);
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
                z_l = CP_L * p_h + p_l * CP + DP_L*k;
                // log2(x_abs) = (ss + ..)*2/(3*log2) = n + DP_H + z_h + z_l
                t = (double)n;
                t1 = (((z_h + z_l) + DP_H*k) + t);
                t1 = __LO(t1, 0);
                t2 = z_l - (((t1 - t) - DP_H*k) - z_h);
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
            } else if ((j & EXP_SIGNIF_BITS) >= 0x4090cc00 ) {        // z <= -1075
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
            i = j & EXP_SIGNIF_BITS;
            k = (i >> 20) - 0x3ff;
            n = 0;
            if (i > 0x3fe00000) {              // if |z| > 0.5, set n = [z + 0.5]
                n = j + (0x00100000 >> (k + 1));
                k = ((n & EXP_SIGNIF_BITS) >> 20) - 0x3ff;     // new k for n
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
        private Exp() {throw new UnsupportedOperationException();}

        private static final double huge    = 1.0e+300;
        private static final double twom1000=     0x1.0p-1000;             //  9.33263618503218878990e-302 = 2^-1000
        private static final double o_threshold=  0x1.62e42fefa39efp9;     //  7.09782712893383973096e+02
        private static final double u_threshold= -0x1.74910d52d3051p9;     // -7.45133219101941108420e+02;
        private static final double ln2HI   =     0x1.62e42feep-1;         //  6.93147180369123816490e-01

        @Stable
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
            hx &= EXP_SIGNIF_BITS;              /* high word of |x| */

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
                    hi = x - ln2HI*(1 - 2*xsb); /* +/- ln2HI */
                    lo=ln2LO[xsb];
                    k = 1 - xsb - xsb;
                } else {
                    k  = (int)(invln2 * x + 0.5 * (1 - 2*xsb) /* +/- 0.5 */  );
                    t  = k;
                    hi = x - t*ln2HI;    /* t*ln2HI is exact here */
                    lo = t*ln2LO[0];
                }
                x  = hi - lo;
            } else if (hx < 0x3e300000)  {     /* when |x|<2**-28 */
                if (huge + x > 1.0)
                    return 1.0 + x; /* trigger inexact */
            } else {
                k = 0;
            }

            /* x is now in primary range */
            t  = x * x;
            c  = x - t*(P1 + t*(P2 + t*(P3 + t*(P4 + t*P5))));
            if (k == 0)
                return 1.0 - ((x*c)/(c - 2.0) - x);
            else
                y = 1.0 - ((lo - (x*c)/(2.0 - c)) - hi);

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

        static double compute(double x) {
            double hfsq, f, s, z, R, w, t1, t2, dk;
            int k, hx, i, j;
            /*unsigned*/ int lx;

            hx = __HI(x);           // high word of x
            lx = __LO(x);           // low  word of x

            k=0;
            if (hx < 0x0010_0000) {                  // x < 2**-1022
                if (((hx & EXP_SIGNIF_BITS) | lx) == 0) { // log(+-0) = -inf
                    return -TWO54/0.0;
                }
                if (hx < 0) {                        // log(-#) = NaN
                    return (x - x)/0.0;
                }
                k -= 54;
                x *= TWO54;    // subnormal number, scale up x
                hx = __HI(x);  // high word of x
            }
            if (hx >= EXP_BITS) {
                return x + x;
            }
            k += (hx >> 20) - 1023;
            hx &= 0x000f_ffff;
            i = (hx + 0x9_5f64) & 0x10_0000;
            x =__HI(x, hx | (i ^ 0x3ff0_0000));  // normalize x or x/2
            k += (i >> 20);
            f = x - 1.0;
            if ((0x000f_ffff & (2 + hx)) < 3) {// |f| < 2**-20
                if (f == 0.0) {
                    if (k == 0) {
                        return 0.0;
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
    static final class Log10 {
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
                if (((hx & EXP_SIGNIF_BITS) | lx) == 0) {
                    return -TWO54/0.0;               /* log(+-0)=-inf */
                }
                if (hx < 0) {
                    return (x - x)/0.0;              /* log(-#) = NaN */
                }
                k -= 54;
                x *= TWO54; /* subnormal number, scale up x */
                hx = __HI(x);
            }

            if (hx >= EXP_BITS) {
                return x + x;
            }

            k += (hx >> 20) - 1023;
            i  = (k  & SIGN_BIT) >>> 31; // unsigned shift
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
    static final class Log1p {
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
            ax = hx & EXP_SIGNIF_BITS;

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

            if (hx >= EXP_BITS) {
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
    static final class Expm1 {
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
            xsb = hx & SIGN_BIT;                 // sign bit of x
            hx &= EXP_SIGNIF_BITS;               // high word of |x|

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
                        return tiny - 1.0;        // return -1
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
            r1 = 1.0 + hxs*(Q1 + hxs*(Q2 + hxs*(Q3 + hxs*(Q4 + hxs*Q5))));
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
                        return 1.0 + 2.0*(x - e);
                    }
                }
                if (k <= -2 || k > 56) {   // suffice to return exp(x) - 1
                    y = 1.0 - (e - x);
                    y = __HI(y, __HI(y) + (k << 20));     // add k to y's exponent
                    return y - 1.0;
                }
                t = 1.0;
                if (k < 20) {
                    t = __HI(t, 0x3ff0_0000 - (0x2_00000 >> k));  // t = 1-2^-k
                    y = t - ( e - x);
                    y = __HI(y, __HI(y) + (k << 20));     // add k to y's exponent
                } else {
                    t = __HI(t, ((0x3ff - k) << 20));     // 2^-k
                    y = x - (e + t);
                    y += 1.0;
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

        private static final double shuge = 1.0e307;

        static double compute(double x) {
            double t, w, h;
            int ix, jx;
            /* unsigned */ int lx;

            // High word of |x|
            jx = __HI(x);
            ix = jx & EXP_SIGNIF_BITS;

            // x is INF or NaN
            if (ix >= EXP_BITS) {
                return x + x;
            }

            h = 0.5;
            if (jx < 0) {
                h = -h;
            }
            // |x| in [0,22], return sign(x)*0.5*(E+E/(E+1)))
            if (ix < 0x4036_0000) {          // |x| < 22
                if (ix < 0x3e30_0000)        // |x| < 2**-28
                    if (shuge + x > 1.0) {   // sinh(tiny) = tiny with inexact
                        return x;
                    }
                t = StrictMath.expm1(Math.abs(x));
                if (ix < 0x3ff0_0000) {
                    return h*(2.0 * t - t*t/(t + 1.0));
                }
                return h*(t + t/(t + 1.0));
            }

            // |x| in [22, log(maxdouble)] return 0.5*exp(|x|)
            if (ix < 0x4086_2E42) {
                return h*StrictMath.exp(Math.abs(x));
            }

            // |x| in [log(maxdouble), overflowthreshold]
            lx = __LO(x);
            if (ix < 0x4086_33CE ||
                ((ix == 0x4086_33ce) &&
                 (Long.compareUnsigned(lx, 0x8fb9_f87d) <= 0 ))) {
                w = StrictMath.exp(0.5 * Math.abs(x));
                t = h * w;
                return t * w;
            }

            // |x| > overflowthreshold, sinh(x) overflow
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

        private static final double huge = 1.0e300;

        static double compute(double x) {
            double t, w;
            int ix;
            /*unsigned*/ int lx;

            // High word of |x|
            ix = __HI(x);
            ix &= EXP_SIGNIF_BITS;

            // x is INF or NaN
            if (ix >= EXP_BITS) {
                return x*x;
            }

            // |x| in [0,0.5*ln2], return 1+expm1(|x|)^2/(2*exp(|x|))
            if (ix < 0x3fd6_2e43) {
                t = StrictMath.expm1(Math.abs(x));
                w = 1.0 + t;
                if (ix < 0x3c80_0000) { // cosh(tiny) = 1
                    return w;
                }
                return 1.0 + (t * t)/(w + w);
            }

            // |x| in [0.5*ln2, 22], return (exp(|x|) + 1/exp(|x|)/2
            if (ix < 0x4036_0000) {
                t = StrictMath.exp(Math.abs(x));
                return 0.5*t + 0.5/t;
            }

            // |x| in [22, log(maxdouble)] return 0.5*exp(|x|)
            if (ix < 0x4086_2E42) {
                return 0.5*StrictMath.exp(Math.abs(x));
            }

            // |x| in [log(maxdouble), overflowthreshold]
            lx = __LO(x);
            if (ix<0x4086_33CE ||
                ((ix == 0x4086_33ce) &&
                 (Integer.compareUnsigned(lx, 0x8fb9_f87d) <= 0))) {
                w = StrictMath.exp(0.5*Math.abs(x));
                t = 0.5*w;
                return t*w;
            }

            // |x| > overflowthreshold, cosh(x) overflow
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

        private static final double tiny = 1.0e-300;

        static double compute(double x) {
            double t, z;
            int jx, ix;

            // High word of |x|.
            jx = __HI(x);
            ix = jx & EXP_SIGNIF_BITS;

            // x is INF or NaN
            if (ix >= EXP_BITS) {
                if (jx >= 0) {  // tanh(+-inf)=+-1
                    return 1.0/x + 1.0;
                } else {        // tanh(NaN) = NaN
                    return 1.0/x - 1.0;
                }
            }

            // |x| < 22
            if (ix < 0x4036_0000) {          // |x| < 22
                if (ix<0x3c80_0000)          // |x| < 2**-55
                    return x*(1.0 + x);      // tanh(small) = small
                if (ix>=0x3ff0_0000) {       // |x| >= 1
                    t = StrictMath.expm1(2.0*Math.abs(x));
                    z = 1.0 - 2.0/(t + 2.0);
                } else {
                    t = StrictMath.expm1(-2.0*Math.abs(x));
                    z= -t/(t + 2.0);
                }
            } else { // |x| > 22, return +-1
                z = 1.0 - tiny;             // raised inexact flag
            }
            return (jx >= 0)? z: -z;
        }
    }

    static final class IEEEremainder {
        private IEEEremainder() {throw new UnsupportedOperationException();}

        static double compute(double x, double p) {
            int hx, hp;
            /*unsigned*/ int sx, lx, lp;
            double p_half;

            hx = __HI(x);           // high word of x
            lx = __LO(x);           // low  word of x
            hp = __HI(p);           // high word of p
            lp = __LO(p);           // low  word of p
            sx = hx & SIGN_BIT;
            hp &= EXP_SIGNIF_BITS;
            hx &= EXP_SIGNIF_BITS;

            // purge off exception values
            if ((hp | lp) == 0) {// p = 0
                return (x*p)/(x*p);
            }
            if ((hx >= EXP_BITS) ||                   // not finite
                ((hp >= EXP_BITS) &&                   // p is NaN
                 (((hp - EXP_BITS) | lp) != 0)))
                return (x*p)/(x*p);

            if (hp <= 0x7fdf_ffff) {  // now x < 2p
                x = __ieee754_fmod(x, p + p);
            }
            if (((hx - hp) | (lx - lp)) == 0) {
                return 0.0*x;
            }
            x  = Math.abs(x);
            p  = Math.abs(p);
            if (hp < 0x0020_0000) {
                if (x + x > p) {
                    x -= p;
                    if (x + x >= p) {
                        x -= p;
                    }
                }
            } else {
                p_half = 0.5*p;
                if (x > p_half) {
                    x -= p;
                    if (x >= p_half) {
                        x -= p;
                    }
                }
            }
            return __HI(x, __HI(x)^sx);
        }

        private static double __ieee754_fmod(double x, double y) {
            int n, hx, hy, hz, ix, iy, sx;
            /*unsigned*/ int lx, ly, lz;

            hx = __HI(x);           // high word of x
            lx = __LO(x);           // low  word of x
            hy = __HI(y);           // high word of y
            ly = __LO(y);           // low  word of y
            sx = hx & SIGN_BIT;     // sign of x
            hx ^= sx;               // |x|
            hy &= EXP_SIGNIF_BITS;  // |y|

            // purge off exception values
            if ((hy | ly) == 0 || (hx >= EXP_BITS)||       // y = 0, or x not finite
               ((hy | ((ly | -ly) >>> 31)) > EXP_BITS))    // or y is NaN, unsigned shift
                return (x*y)/(x*y);
            if (hx <= hy) {
                if ((hx < hy) || (Integer.compareUnsigned(lx, ly) < 0)) { // |x| < |y| return x
                    return x;
                }
                if (lx == ly) {
                    return signedZero(sx);  // |x| = |y| return x*0
                }
            }

            ix = ilogb(hx, lx);
            iy = ilogb(hy, ly);

            // set up {hx, lx}, {hy, ly} and align y to x
            if (ix >= -1022)
                hx = 0x0010_0000 | (0x000f_ffff & hx);
            else {          // subnormal x, shift x to normal
                n = -1022 - ix;
                if (n <= 31) {
                    hx = (hx << n) | (lx >>> (32 - n)); // unsigned shift
                    lx <<= n;
                } else {
                    hx = lx << (n - 32);
                    lx = 0;
                }
            }
            if (iy >= -1022)
                hy = 0x0010_0000 | (0x000f_ffff & hy);
            else {          // subnormal y, shift y to normal
                n = -1022 - iy;
                if (n <= 31) {
                    hy = (hy << n)|(ly >>> (32 - n)); // unsigned shift
                    ly <<= n;
                } else {
                    hy = ly << (n - 32);
                    ly = 0;
                }
            }

            // fix point fmod
            n = ix - iy;
            while (n-- != 0) {
                hz = hx - hy;
                lz = lx - ly;
                if (Integer.compareUnsigned(lx, ly) < 0) {
                    hz -= 1;
                }
                if (hz < 0){
                    hx = hx + hx +(lx >>> 31); // unsigned shift
                    lx = lx + lx;
                } else {
                    if ((hz | lz) == 0) {        // return sign(x)*0
                        return signedZero(sx);
                    }
                    hx = hz + hz + (lz >>> 31); // unsigned shift
                    lx = lz + lz;
                }
            }
            hz = hx - hy;
            lz = lx - ly;
            if (Integer.compareUnsigned(lx, ly) < 0) {
                hz -= 1;
            }
            if (hz >= 0) {
                hx = hz;
                lx = lz;
            }

            // convert back to floating value and restore the sign
            if ((hx | lx) == 0) {                  // return sign(x)*0
                return signedZero(sx);
            }
            while (hx < 0x0010_0000) {      // normalize x
                hx = hx + hx + (lx >>> 31); // unsigned shift
                lx = lx + lx;
                iy -= 1;
            }
            if (iy >= -1022) {        // normalize output
                hx = ((hx - 0x0010_0000) | ((iy + 1023) << 20));
                x = __HI_LO(hx | sx, lx);
            } else {                // subnormal output
                n = -1022 - iy;

                if (n <= 20) {
                    lx = (lx >>> n)|(/*(unsigned)*/hx << (32 - n)); // unsigned shift
                    hx >>= n;
                } else if (n <= 31) {
                    lx = (hx << (32 - n))|(lx >>> n); // unsigned shift
                    hx = sx;
                } else {
                    lx = hx >>(n - 32);
                    hx = sx;
                }
                x = __HI_LO(hx | sx, lx);
                x *= 1.0;           // create necessary signal
            }
            return x;               // exact output
        }

        /*
         * Return a double zero with the same sign as the int argument.
         */
        private static double signedZero(int sign) {
            return +0.0 * ( (double)sign);
        }

        private static int ilogb(int hz, int lz) {
            int iz, i;
            if (hz < 0x0010_0000) {     // subnormal z
                if (hz == 0) {
                    for (iz = -1043, i = lz; i > 0; i <<= 1) {
                        iz -= 1;
                    }
                } else {
                    for (iz = -1022, i = (hz << 11); i > 0; i <<= 1) {
                        iz -= 1;
                    }
                }
            } else {
                iz = (hz >> 20) - 1023;
            }
            return iz;
        }
    }

    /**
     * Return the Inverse Hyperbolic Sine of x
     *
     * Method :
     *
     *
     *      asinh(x) is defined so that asinh(sinh(alpha)) = alpha, -&infin; &lt; alpha &lt; &infin;
     *      and sinh(asinh(x)) = x, -&infin; &lt; x &lt; &infin;
     *      It can be written as asinh(x) = sign(x) * ln(|x| + sqrt(x^2 + 1)), -&infin; &lt; x &lt; &infin;
     *      1. Replace x by |x| as the function is odd.
     *      2.
     *          asinh(x) := x, if 1+x^2 = 1,
     *                   := sign(x)*(log(x)+ln2)) for large |x|, else
     *                   := sign(x)*log(2|x|+1/(|x|+sqrt(x*x+1))) if|x|>2, else
     *                   := sign(x)*log1p(|x| + x^2/(1 + sqrt(1+x^2)))
     *
     *
     *
     * Special cases:
     *      only asinh(&plusmn;0)=&plusmn;0 is exact for finite x.
     *      asinh(NaN) is NaN
     *      asinh(&plusmn;&infin;) is &plusmn;&infin;
     */
    static final class Asinh {
        private static final double ln2 = 6.93147180559945286227e-01;
        private static final double huge = 1.0e300;

        static double compute(double x) {
            double t, w;
            int hx, ix;
            hx = __HI(x);
            ix = hx & 0x7fff_ffff;
            if (ix >= 0x7ff0_0000) {
                return x + x;                       // x is inf or NaN
            }
            if (ix < 0x3e30_0000) {                 // |x| < 2**-28
                if (huge + x > 1.0) {
                    return x;                       // return x inexact except 0
                }
            }
            if (ix > 0x41b0_0000) {                 // |x| > 2**28
                w = Log.compute(Math.abs(x)) + ln2;
            } else if (ix > 0x4000_0000) {          // 2**28 > |x| > 2.0
                t = Math.abs(x);
                w = Log.compute(2.0 * t + 1.0 / (Sqrt.compute(x * x + 1.0) + t));
            } else {                                // 2.0 > |x| > 2**-28
                t = x * x;
                w = Log1p.compute(Math.abs(x) + t / (1.0 + Sqrt.compute(1.0 + t)));
            }
            return hx > 0 ? w : -w;
        }
    }

    /**
     * Return the Inverse Hyperbolic Cosine of x
     *
     * Method :
     *
     *
     *      acosh(x) is defined so that acosh(cosh(alpha)) = alpha, -&infin; &lt; alpha &lt; &infin;
     *      and cosh(acosh(x)) = x, 1 <= x &lt; &infin;.
     *      It can be written as acosh(x) = log(x + sqrt(x^2 - 1)), 1 <= x  &lt; &infin;.
     *      acosh(x) := log(x)+ln2, if x is large; else
     *               := log(2x-1/(sqrt(x*x-1)+x)) if x&gt;2; else
     *               := log1p(t+sqrt(2.0*t+t*t)); where t=x-1.
     *
     *
     *
     * Special cases:
     *      acosh(x) is NaN with signal if x < 1.
     *      acosh(NaN) is NaN without signal.
     */
    static final class Acosh {
        private static final double ln2 = 6.93147180559945286227e-01;

        static double compute(double x) {
            double t;
            int hx;
            hx = __HI(x);
            if (hx < 0x3ff0_0000) {                           // x < 1 */
                return (x - x) / (x - x);
            } else if (hx >= 0x41b0_0000) {                   // x > 2**28
                if (hx >= 0x7ff0_0000) {                      // x is inf of NaN
                    return x + x;
                } else {
                    return Log.compute(x) + ln2;              // acosh(huge) = log(2x)
                }
            } else if (((hx - 0x3ff0_0000) | __LO(x)) == 0) {
                return 0.0;                                   // acosh(1) = 0
            } else if (hx > 0x4000_0000) {                    // 2**28 > x > 2
                t = x * x;
                return Log.compute(2.0 * x - 1.0 / (x + Sqrt.compute(t - 1.0)));
            } else {                                          // 1< x <2
                t = x - 1.0;
                return Log1p.compute(t + Sqrt.compute(2.0 * t + t * t));
            }
        }
    }
}
