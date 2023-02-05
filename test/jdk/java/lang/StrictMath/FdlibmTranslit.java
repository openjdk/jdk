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

/**
 * A transliteration of the "Freely Distributable Math Library"
 * algorithms from C into Java. That is, this port of the algorithms
 * is as close to the C originals as possible while still being
 * readable legal Java.
 */
public class FdlibmTranslit {
    private FdlibmTranslit() {
        throw new UnsupportedOperationException("No FdLibmTranslit instances for you.");
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

    public static double hypot(double x, double y) {
        return Hypot.compute(x, y);
    }

    public static double cbrt(double x) {
        return Cbrt.compute(x);
    }

    public static double log10(double x) {
        return Log10.compute(x);
    }

    public static double log1p(double x) {
        return Log1p.compute(x);
    }

    public static double expm1(double x) {
        return Expm1.compute(x);
    }

    public static double sinh(double x) {
        return Sinh.compute(x);
    }

    public static double cosh(double x) {
        return Cosh.compute(x);
    }

    public static double tanh(double x) {
        return Tanh.compute(x);
    }


    /**
     * cbrt(x)
     * Return cube root of x
     */
    public static class Cbrt {
        // unsigned
        private static final int B1 = 715094163; /* B1 = (682-0.03306235651)*2**20 */
        private static final int B2 = 696219795; /* B2 = (664-0.03306235651)*2**20 */

        private static final double C =  5.42857142857142815906e-01; /* 19/35     = 0x3FE15F15, 0xF15F15F1 */
        private static final double D = -7.05306122448979611050e-01; /* -864/1225 = 0xBFE691DE, 0x2532C834 */
        private static final double E =  1.41428571428571436819e+00; /* 99/70     = 0x3FF6A0EA, 0x0EA0EA0F */
        private static final double F =  1.60714285714285720630e+00; /* 45/28     = 0x3FF9B6DB, 0x6DB6DB6E */
        private static final double G =  3.57142857142857150787e-01; /* 5/14      = 0x3FD6DB6D, 0xB6DB6DB7 */

        public static double compute(double x) {
            int     hx;
            double  r, s, t=0.0, w;
            int sign; // unsigned

            hx = __HI(x);           // high word of x
            sign = hx & 0x80000000;             // sign= sign(x)
            hx  ^= sign;
            if (hx >= 0x7ff00000)
                return (x+x); // cbrt(NaN,INF) is itself
            if ((hx | __LO(x)) == 0)
                return(x);          // cbrt(0) is itself

            x = __HI(x, hx);   // x <- |x|
            // rough cbrt to 5 bits
            if (hx < 0x00100000) {               // subnormal number
                t = __HI(t, 0x43500000);          // set t= 2**54
                t *= x;
                t = __HI(t, __HI(t)/3+B2);
            } else {
                t = __HI(t, hx/3+B1);
            }

            // new cbrt to 23 bits, may be implemented in single precision
            r = t * t/x;
            s = C + r*t;
            t *= G + F/(s + E + D/s);

            // chopped to 20 bits and make it larger than cbrt(x)
            t = __LO(t, 0);
            t = __HI(t, __HI(t)+0x00000001);


            // one step newton iteration to 53 bits with error less than 0.667 ulps
            s = t * t;          // t*t is exact
            r = x / s;
            w = t + t;
            r= (r - t)/(w + r);  // r-s is exact
            t= t + t*r;

            // retore the sign bit
            t = __HI(t, __HI(t) | sign);
            return(t);
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
     *      than 1 ulps (units in the last place)
     */
    static class Hypot {
        public static double compute(double x, double y) {
            double a = x;
            double b = y;
            double t1, t2, y1, y2, w;
            int j, k, ha, hb;

            ha = __HI(x) & 0x7fffffff;        // high word of  x
            hb = __HI(y) & 0x7fffffff;        // high word of  y
            if(hb > ha) {
                a = y;
                b = x;
                j = ha;
                ha = hb;
                hb = j;
            } else {
                a = x;
                b = y;
            }
            a = __HI(a, ha);   // a <- |a|
            b = __HI(b, hb);   // b <- |b|
            if ((ha - hb) > 0x3c00000) {
                return a + b;  // x / y > 2**60
            }
            k=0;
            if (ha > 0x5f300000) {   // a>2**500
                if (ha >= 0x7ff00000) {       // Inf or NaN
                    w = a + b;                // for sNaN
                    if (((ha & 0xfffff) | __LO(a)) == 0)
                        w = a;
                    if (((hb ^ 0x7ff00000) | __LO(b)) == 0)
                        w = b;
                    return w;
                }
                // scale a and b by 2**-600
                ha -= 0x25800000;
                hb -= 0x25800000;
                k += 600;
                a = __HI(a, ha);
                b = __HI(b, hb);
            }
            if (hb < 0x20b00000) {   // b < 2**-500
                if (hb <= 0x000fffff) {      // subnormal b or 0 */
                    if ((hb | (__LO(b))) == 0)
                        return a;
                    t1 = 0;
                    t1 = __HI(t1, 0x7fd00000);  // t1=2^1022
                    b *= t1;
                    a *= t1;
                    k -= 1022;
                } else {            // scale a and b by 2^600
                    ha += 0x25800000;       // a *= 2^600
                    hb += 0x25800000;       // b *= 2^600
                    k -= 600;
                    a = __HI(a, ha);
                    b = __HI(b, hb);
                }
            }
            // medium size a and b
            w = a - b;
            if (w > b) {
                t1 = 0;
                t1 = __HI(t1, ha);
                t2 = a - t1;
                w  = Math.sqrt(t1*t1 - (b*(-b) - t2 * (a + t1)));
            } else {
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
                t1 = 1.0;
                int t1_hi = __HI(t1);
                t1_hi += (k << 20);
                t1 = __HI(t1, t1_hi);
                return t1 * w;
            } else
                return w;
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
    static class Exp {
        private static final double one     = 1.0;
        private static final double[] halF = {0.5,-0.5,};
        private static final double huge    = 1.0e+300;
        private static final double twom1000= 9.33263618503218878990e-302;      /* 2**-1000=0x01700000,0*/
        private static final double o_threshold=  7.09782712893383973096e+02;   /* 0x40862E42, 0xFEFA39EF */
        private static final double u_threshold= -7.45133219101941108420e+02;   /* 0xc0874910, 0xD52D3051 */
        private static final double[] ln2HI   ={ 6.93147180369123816490e-01,    /* 0x3fe62e42, 0xfee00000 */
                                                 -6.93147180369123816490e-01};  /* 0xbfe62e42, 0xfee00000 */
        private static final double[] ln2LO   ={ 1.90821492927058770002e-10,    /* 0x3dea39ef, 0x35793c76 */
                                                 -1.90821492927058770002e-10,}; /* 0xbdea39ef, 0x35793c76 */
        private static final double invln2 =  1.44269504088896338700e+00;       /* 0x3ff71547, 0x652b82fe */
        private static final double P1   =  1.66666666666666019037e-01;         /* 0x3FC55555, 0x5555553E */
        private static final double P2   = -2.77777777770155933842e-03;         /* 0xBF66C16C, 0x16BEBD93 */
        private static final double P3   =  6.61375632143793436117e-05;         /* 0x3F11566A, 0xAF25DE2C */
        private static final double P4   = -1.65339022054652515390e-06;         /* 0xBEBBBD41, 0xC5D26BF1 */
        private static final double P5   =  4.13813679705723846039e-08;         /* 0x3E663769, 0x72BEA4D0 */

        public static double compute(double x) {
            double y,hi=0,lo=0,c,t;
            int k=0,xsb;
            /*unsigned*/ int hx;

            hx  = __HI(x);  /* high word of x */
            xsb = (hx>>31)&1;               /* sign bit of x */
            hx &= 0x7fffffff;               /* high word of |x| */

            /* filter out non-finite argument */
            if(hx >= 0x40862E42) {                  /* if |x|>=709.78... */
                if(hx>=0x7ff00000) {
                    if(((hx&0xfffff)|__LO(x))!=0)
                        return x+x;                /* NaN */
                    else return (xsb==0)? x:0.0;    /* exp(+-inf)={inf,0} */
                }
                if(x > o_threshold) return huge*huge; /* overflow */
                if(x < u_threshold) return twom1000*twom1000; /* underflow */
            }

            /* argument reduction */
            if(hx > 0x3fd62e42) {           /* if  |x| > 0.5 ln2 */
                if(hx < 0x3FF0A2B2) {       /* and |x| < 1.5 ln2 */
                    hi = x-ln2HI[xsb]; lo=ln2LO[xsb]; k = 1-xsb-xsb;
                } else {
                    k  = (int)(invln2*x+halF[xsb]);
                    t  = k;
                    hi = x - t*ln2HI[0];    /* t*ln2HI is exact here */
                    lo = t*ln2LO[0];
                }
                x  = hi - lo;
            }
            else if(hx < 0x3e300000)  {     /* when |x|<2**-28 */
                if(huge+x>one) return one+x;/* trigger inexact */
            }
            else k = 0;

            /* x is now in primary range */
            t  = x*x;
            c  = x - t*(P1+t*(P2+t*(P3+t*(P4+t*P5))));
            if(k==0)        return one-((x*c)/(c-2.0)-x);
            else            y = one-((lo-(x*c)/(2.0-c))-hi);
            if(k >= -1021) {
                y = __HI(y, __HI(y) + (k<<20)); /* add k to y's exponent */
                return y;
            } else {
                y = __HI(y, __HI(y) + ((k+1000)<<20));/* add k to y's exponent */
                return y*twom1000;
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
        private static double two54      =  1.80143985094819840000e+16; /* 0x43500000, 0x00000000 */
        private static double ivln10     =  4.34294481903251816668e-01; /* 0x3FDBCB7B, 0x1526E50E */

        private static double log10_2hi  =  3.01029995663611771306e-01; /* 0x3FD34413, 0x509F6000 */
        private static double log10_2lo  =  3.69423907715893078616e-13; /* 0x3D59FEF3, 0x11F12B36 */

        private static double zero   =  0.0;

        public static double compute(double x) {
            double y,z;
            int i,k,hx;
            /*unsigned*/ int lx;

            hx = __HI(x);   /* high word of x */
            lx = __LO(x);   /* low word of x */

            k=0;
            if (hx < 0x00100000) {                  /* x < 2**-1022  */
                if (((hx&0x7fffffff)|lx)==0)
                    return -two54/zero;             /* log(+-0)=-inf */
                if (hx<0) return (x-x)/zero;        /* log(-#) = NaN */
                k -= 54; x *= two54; /* subnormal number, scale up x */
                hx = __HI(x);                /* high word of x */
            }
            if (hx >= 0x7ff00000) return x+x;
            k += (hx>>20)-1023;
            i  = (k&0x80000000)>>>31; // unsigned shift
            hx = (hx&0x000fffff)|((0x3ff-i)<<20);
            y  = (double)(k+i);
            x = __HI(x, hx); //original: __HI(x) = hx;
            z  = y*log10_2lo + ivln10*StrictMath.log(x); // TOOD: switch to Translit.log when available
            return  z+y*log10_2hi;
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
        private static double ln2_hi  =  6.93147180369123816490e-01;  /* 3fe62e42 fee00000 */
        private static double ln2_lo  =  1.90821492927058770002e-10;  /* 3dea39ef 35793c76 */
        private static double two54   =  1.80143985094819840000e+16;  /* 43500000 00000000 */
        private static double Lp1 = 6.666666666666735130e-01;  /* 3FE55555 55555593 */
        private static double Lp2 = 3.999999999940941908e-01;  /* 3FD99999 9997FA04 */
        private static double Lp3 = 2.857142874366239149e-01;  /* 3FD24924 94229359 */
        private static double Lp4 = 2.222219843214978396e-01;  /* 3FCC71C5 1D8E78AF */
        private static double Lp5 = 1.818357216161805012e-01;  /* 3FC74664 96CB03DE */
        private static double Lp6 = 1.531383769920937332e-01;  /* 3FC39A09 D078C69F */
        private static double Lp7 = 1.479819860511658591e-01;  /* 3FC2F112 DF3E5244 */
        private static double zero = 0.0;

        public static double compute(double x) {
            double hfsq,f=0,c=0,s,z,R,u;
            int k,hx,hu=0,ax;

            hx = __HI(x);           /* high word of x */
            ax = hx&0x7fffffff;

            k = 1;
            if (hx < 0x3FDA827A) {                  /* x < 0.41422  */
                if(ax>=0x3ff00000) {                /* x <= -1.0 */
                    /*
                     * Added redundant test against hx to work around VC++
                     * code generation problem.
                     */
                    if(x==-1.0 && (hx==0xbff00000)) /* log1p(-1)=-inf */
                        return -two54/zero;
                    else
                        return (x-x)/(x-x);           /* log1p(x<-1)=NaN */
                }
                if(ax<0x3e200000) {                 /* |x| < 2**-29 */
                    if(two54+x>zero                 /* raise inexact */
                       &&ax<0x3c900000)            /* |x| < 2**-54 */
                        return x;
                    else
                        return x - x*x*0.5;
                }
                if(hx>0||hx<=((int)0xbfd2bec3)) {
                    k=0;f=x;hu=1;}  /* -0.2929<x<0.41422 */
            }
            if (hx >= 0x7ff00000) return x+x;
            if(k!=0) {
                if(hx<0x43400000) {
                    u  = 1.0+x;
                    hu = __HI(u);           /* high word of u */
                    k  = (hu>>20)-1023;
                    c  = (k>0)? 1.0-(u-x):x-(u-1.0);/* correction term */
                    c /= u;
                } else {
                    u  = x;
                    hu = __HI(u);           /* high word of u */
                    k  = (hu>>20)-1023;
                    c  = 0;
                }
                hu &= 0x000fffff;
                if(hu<0x6a09e) {
                    u = __HI(u, hu|0x3ff00000);        /* normalize u */
                } else {
                    k += 1;
                    u = __HI(u, hu|0x3fe00000);        /* normalize u/2 */
                    hu = (0x00100000-hu)>>2;
                }
                f = u-1.0;
            }
            hfsq=0.5*f*f;
            if(hu==0) {     /* |f| < 2**-20 */
                if(f==zero) { if(k==0) return zero;
                    else {c += k*ln2_lo; return k*ln2_hi+c;}}
                R = hfsq*(1.0-0.66666666666666666*f);
                if(k==0) return f-R; else
                    return k*ln2_hi-((R-(k*ln2_lo+c))-f);
            }
            s = f/(2.0+f);
            z = s*s;
            R = z*(Lp1+z*(Lp2+z*(Lp3+z*(Lp4+z*(Lp5+z*(Lp6+z*Lp7))))));
            if(k==0) return f-(hfsq-s*(hfsq+R)); else
                return k*ln2_hi-((hfsq-(s*(hfsq+R)+(k*ln2_lo+c)))-f);
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
        private static final double one             = 1.0;
        private static final double huge            = 1.0e+300;
        private static final double tiny            = 1.0e-300;
        private static final double o_threshold     = 7.09782712893383973096e+02; /* 0x40862E42, 0xFEFA39EF */
        private static final double ln2_hi          = 6.93147180369123816490e-01; /* 0x3fe62e42, 0xfee00000 */
        private static final double ln2_lo          = 1.90821492927058770002e-10; /* 0x3dea39ef, 0x35793c76 */
        private static final double invln2          = 1.44269504088896338700e+00; /* 0x3ff71547, 0x652b82fe */
        /* scaled coefficients related to expm1 */
        private static final double Q1  =  -3.33333333333331316428e-02; /* BFA11111 111110F4 */
        private static final double Q2  =   1.58730158725481460165e-03; /* 3F5A01A0 19FE5585 */
        private static final double Q3  =  -7.93650757867487942473e-05; /* BF14CE19 9EAADBB7 */
        private static final double Q4  =   4.00821782732936239552e-06; /* 3ED0CFCA 86E65239 */
        private static final double Q5  =  -2.01099218183624371326e-07; /* BE8AFDB7 6E09C32D */

        static double compute(double x) {
            double y,hi,lo,c=0,t,e,hxs,hfx,r1;
            int k,xsb;
            /*unsigned*/ int hx;

            hx  = __HI(x);  /* high word of x */
            xsb = hx&0x80000000;            /* sign bit of x */
            if(xsb==0) y=x; else y= -x;     /* y = |x| */
            hx &= 0x7fffffff;               /* high word of |x| */

            /* filter out huge and non-finite argument */
            if(hx >= 0x4043687A) {                  /* if |x|>=56*ln2 */
                if(hx >= 0x40862E42) {              /* if |x|>=709.78... */
                    if(hx>=0x7ff00000) {
                        if(((hx&0xfffff)|__LO(x))!=0)
                            return x+x;     /* NaN */
                        else return (xsb==0)? x:-1.0;/* exp(+-inf)={inf,-1} */
                    }
                    if(x > o_threshold) return huge*huge; /* overflow */
                }
                if(xsb!=0) { /* x < -56*ln2, return -1.0 with inexact */
                    if(x+tiny<0.0)          /* raise inexact */
                        return tiny-one;        /* return -1 */
                }
            }

            /* argument reduction */
            if(hx > 0x3fd62e42) {           /* if  |x| > 0.5 ln2 */
                if(hx < 0x3FF0A2B2) {       /* and |x| < 1.5 ln2 */
                    if(xsb==0)
                        {hi = x - ln2_hi; lo =  ln2_lo;  k =  1;}
                    else
                        {hi = x + ln2_hi; lo = -ln2_lo;  k = -1;}
                } else {
                    k  = (int)(invln2*x+((xsb==0)?0.5:-0.5));
                    t  = k;
                    hi = x - t*ln2_hi;      /* t*ln2_hi is exact here */
                    lo = t*ln2_lo;
                }
                x  = hi - lo;
                c  = (hi-x)-lo;
            }
            else if(hx < 0x3c900000) {      /* when |x|<2**-54, return x */
                t = huge+x; /* return x with inexact flags when x!=0 */
                return x - (t-(huge+x));
            }
            else k = 0;

            /* x is now in primary range */
            hfx = 0.5*x;
            hxs = x*hfx;
            r1 = one+hxs*(Q1+hxs*(Q2+hxs*(Q3+hxs*(Q4+hxs*Q5))));
            t  = 3.0-r1*hfx;
            e  = hxs*((r1-t)/(6.0 - x*t));
            if(k==0) return x - (x*e-hxs);          /* c is 0 */
            else {
                e  = (x*(e-c)-c);
                e -= hxs;
                if(k== -1) return 0.5*(x-e)-0.5;
                if(k==1) {
                    if(x < -0.25) return -2.0*(e-(x+0.5));
                    else          return  one+2.0*(x-e);
                }
                if (k <= -2 || k>56) {   /* suffice to return exp(x)-1 */
                    y = one-(e-x);
                    y = __HI(y,  __HI(y) + (k<<20));     /* add k to y's exponent */
                    return y-one;
                }
                t = one;
                if(k<20) {
                    t = __HI(t, 0x3ff00000 - (0x200000>>k));  /* t=1-2^-k */
                    y = t-(e-x);
                    y = __HI(y, __HI(y) + (k<<20));     /* add k to y's exponent */
                } else {
                    t = __HI(t, ((0x3ff-k)<<20));     /* 2^-k */
                    y = x-(e+t);
                    y += one;
                    y = __HI(y, __HI(y) + (k<<20));     /* add k to y's exponent */
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
    static class Sinh {
        private static final double one = 1.0, shuge = 1.0e307;

        private static double compute(double x) {
            double t,w,h;
            int ix,jx;
            /* unsigned */ int lx;

            /* High word of |x|. */
            jx = __HI(x);
            ix = jx&0x7fffffff;

            /* x is INF or NaN */
            if(ix>=0x7ff00000) return x+x;

            h = 0.5;
            if (jx<0) h = -h;
            /* |x| in [0,22], return sign(x)*0.5*(E+E/(E+1))) */
            if (ix < 0x40360000) {          /* |x|<22 */
                if (ix<0x3e300000)          /* |x|<2**-28 */
                    if(shuge+x>one) return x;/* sinh(tiny) = tiny with inexact */
                t = FdlibmTranslit.expm1(Math.abs(x));
                if(ix<0x3ff00000) return h*(2.0*t-t*t/(t+one));
                return h*(t+t/(t+one));
            }

            /* |x| in [22, log(maxdouble)] return 0.5*exp(|x|) */
            if (ix < 0x40862E42)  return h*StrictMath.exp(Math.abs(x)); // TODO switch to translit

            /* |x| in [log(maxdouble), overflowthresold] */
            // lx = *( (((*(unsigned*)&one)>>29)) + (unsigned*)&x);
            // lx =  (((*(unsigned*)&one)>>29)) + (unsigned*)&x ;
            lx = __LO(x);
            if (ix<0x408633CE || ((ix==0x408633ce)&&(Long.compareUnsigned(lx, 0x8fb9f87d) <= 0 ))) {
                w = StrictMath.exp(0.5*Math.abs(x)); // TODO switch to translit
                t = h*w;
                return t*w;
            }

            /* |x| > overflowthresold, sinh(x) overflow */
            return x*shuge;
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
    static class Cosh {
        private static final double one = 1.0, half=0.5, huge = 1.0e300;
        private static double compute(double x) {
            double t,w;
            int ix;
            /*unsigned*/ int lx;

            /* High word of |x|. */
            ix = __HI(x);
            ix &= 0x7fffffff;

            /* x is INF or NaN */
            if(ix>=0x7ff00000) return x*x;

            /* |x| in [0,0.5*ln2], return 1+expm1(|x|)^2/(2*exp(|x|)) */
            if(ix<0x3fd62e43) {
                t = expm1(Math.abs(x));
                w = one+t;
                if (ix<0x3c800000) return w;        /* cosh(tiny) = 1 */
                return one+(t*t)/(w+w);
            }

            /* |x| in [0.5*ln2,22], return (exp(|x|)+1/exp(|x|)/2; */
            if (ix < 0x40360000) {
                t = StrictMath.exp(Math.abs(x)); // TODO switch to translit
                return half*t+half/t;
            }

            /* |x| in [22, log(maxdouble)] return half*exp(|x|) */
            if (ix < 0x40862E42)  return half*StrictMath.exp(Math.abs(x)); // TODO switch to translit

            /* |x| in [log(maxdouble), overflowthresold] */
            lx = __LO(x);
            if (ix<0x408633CE ||
                ((ix==0x408633ce)&&(Integer.compareUnsigned(lx, 0x8fb9f87d) <= 0))) {
                w = StrictMath.exp(half*Math.abs(x)); // TODO switch to translit
                t = half*w;
                return t*w;
            }

            /* |x| > overflowthresold, cosh(x) overflow */
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
    static class Tanh {
        private static final double one=1.0, two=2.0, tiny = 1.0e-300;
        static double compute(double x) {
            double t,z;
            int jx,ix;

            /* High word of |x|. */
            jx = __HI(x);
            ix = jx&0x7fffffff;

            /* x is INF or NaN */
            if(ix>=0x7ff00000) {
                if (jx>=0) return one/x+one;    /* tanh(+-inf)=+-1 */
                else       return one/x-one;    /* tanh(NaN) = NaN */
            }

            /* |x| < 22 */
            if (ix < 0x40360000) {          /* |x|<22 */
                if (ix<0x3c800000)          /* |x|<2**-55 */
                    return x*(one+x);       /* tanh(small) = small */
                if (ix>=0x3ff00000) {       /* |x|>=1  */
                    t = expm1(two*Math.abs(x));
                    z = one - two/(t+two);
                } else {
                    t = expm1(-two*Math.abs(x));
                    z= -t/(t+two);
                }
                /* |x| > 22, return +-1 */
            } else {
                z = one - tiny;             /* raised inexact flag */
            }
            return (jx>=0)? z: -z;
        }
    }
}
