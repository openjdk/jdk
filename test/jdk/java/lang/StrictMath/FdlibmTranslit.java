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

    public static double asin(double x) {
        return Asin.compute(x);
    }

    public static double acos(double x) {
        return Acos.compute(x);
    }

    public static double atan(double x) {
        return Atan.compute(x);
    }

    public static double atan2(double y, double x) {
        return Atan2.compute(y, x);
    }

    public static double hypot(double x, double y) {
        return Hypot.compute(x, y);
    }

    public static double sqrt(double x) {
        return Sqrt.compute(x);
    }

    public static double cbrt(double x) {
        return Cbrt.compute(x);
    }

    public static double log(double x) {
        return Log.compute(x);
    }

    public static double log10(double x) {
        return Log10.compute(x);
    }

    public static double log1p(double x) {
        return Log1p.compute(x);
    }

    public static double exp(double x) {
        return Exp.compute(x);
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
    static class Asin {
        private static final double
            one =  1.00000000000000000000e+00, /* 0x3FF00000, 0x00000000 */
            huge =  1.000e+300,
            pio2_hi =  1.57079632679489655800e+00, /* 0x3FF921FB, 0x54442D18 */
            pio2_lo =  6.12323399573676603587e-17, /* 0x3C91A626, 0x33145C07 */
            pio4_hi =  7.85398163397448278999e-01, /* 0x3FE921FB, 0x54442D18 */
        /* coefficient for R(x^2) */
            pS0 =  1.66666666666666657415e-01, /* 0x3FC55555, 0x55555555 */
            pS1 = -3.25565818622400915405e-01, /* 0xBFD4D612, 0x03EB6F7D */
            pS2 =  2.01212532134862925881e-01, /* 0x3FC9C155, 0x0E884455 */
            pS3 = -4.00555345006794114027e-02, /* 0xBFA48228, 0xB5688F3B */
            pS4 =  7.91534994289814532176e-04, /* 0x3F49EFE0, 0x7501B288 */
            pS5 =  3.47933107596021167570e-05, /* 0x3F023DE1, 0x0DFDF709 */
            qS1 = -2.40339491173441421878e+00, /* 0xC0033A27, 0x1C8A2D4B */
            qS2 =  2.02094576023350569471e+00, /* 0x40002AE5, 0x9C598AC8 */
            qS3 = -6.88283971605453293030e-01, /* 0xBFE6066C, 0x1B8D0159 */
            qS4 =  7.70381505559019352791e-02; /* 0x3FB3B8C5, 0xB12E9282 */

        static double compute(double x) {
            double t=0,w,p,q,c,r,s;
            int hx,ix;
            hx = __HI(x);
            ix = hx&0x7fffffff;
            if(ix>= 0x3ff00000) {           /* |x|>= 1 */
                if(((ix-0x3ff00000)|__LO(x))==0)
                    /* asin(1)=+-pi/2 with inexact */
                    return x*pio2_hi+x*pio2_lo;
                return (x-x)/(x-x);         /* asin(|x|>1) is NaN */
            } else if (ix<0x3fe00000) {     /* |x|<0.5 */
                if(ix<0x3e400000) {         /* if |x| < 2**-27 */
                    if(huge+x>one) return x;/* return x with inexact if x!=0*/
                } else
                    t = x*x;
                p = t*(pS0+t*(pS1+t*(pS2+t*(pS3+t*(pS4+t*pS5)))));
                q = one+t*(qS1+t*(qS2+t*(qS3+t*qS4)));
                w = p/q;
                return x+x*w;
            }
            /* 1> |x|>= 0.5 */
            w = one-Math.abs(x);
            t = w*0.5;
            p = t*(pS0+t*(pS1+t*(pS2+t*(pS3+t*(pS4+t*pS5)))));
            q = one+t*(qS1+t*(qS2+t*(qS3+t*qS4)));
            s = Math.sqrt(t);
            if(ix>=0x3FEF3333) {    /* if |x| > 0.975 */
                w = p/q;
                t = pio2_hi-(2.0*(s+s*w)-pio2_lo);
            } else {
                w  = s;
                // __LO(w) = 0;
                w  = __LO(w, 0);
                c  = (t-w*w)/(s+w);
                r  = p/q;
                p  = 2.0*s*r-(pio2_lo-2.0*c);
                q  = pio4_hi-2.0*w;
                t  = pio4_hi-(p-q);
            }
            if(hx>0) return t; else return -t;
        }
    }

    /** Returns the arccosine of x.
     * Method :
     *      acos(x)  = pi/2 - asin(x)
     *      acos(-x) = pi/2 + asin(x)
     * For |x|<=0.5
     *      acos(x) = pi/2 - (x + x*x^2*R(x^2))     (see asin.c)
     * For x>0.5
     *      acos(x) = pi/2 - (pi/2 - 2asin(sqrt((1-x)/2)))
     *              = 2asin(sqrt((1-x)/2))
     *              = 2s + 2s*z*R(z)        ...z=(1-x)/2, s=sqrt(z)
     *              = 2f + (2c + 2s*z*R(z))
     *     where f=hi part of s, and c = (z-f*f)/(s+f) is the correction term
     *     for f so that f+c ~ sqrt(z).
     * For x<-0.5
     *      acos(x) = pi - 2asin(sqrt((1-|x|)/2))
     *              = pi - 0.5*(s+s*z*R(z)), where z=(1-|x|)/2,s=sqrt(z)
     *
     * Special cases:
     *      if x is NaN, return x itself;
     *      if |x|>1, return NaN with invalid signal.
     *
     * Function needed: sqrt
     */
    static class Acos {
        private static final double
            one=  1.00000000000000000000e+00, /* 0x3FF00000, 0x00000000 */
            pi =  3.14159265358979311600e+00, /* 0x400921FB, 0x54442D18 */
            pio2_hi =  1.57079632679489655800e+00, /* 0x3FF921FB, 0x54442D18 */
            pio2_lo =  6.12323399573676603587e-17, /* 0x3C91A626, 0x33145C07 */
            pS0 =  1.66666666666666657415e-01, /* 0x3FC55555, 0x55555555 */
            pS1 = -3.25565818622400915405e-01, /* 0xBFD4D612, 0x03EB6F7D */
            pS2 =  2.01212532134862925881e-01, /* 0x3FC9C155, 0x0E884455 */
            pS3 = -4.00555345006794114027e-02, /* 0xBFA48228, 0xB5688F3B */
            pS4 =  7.91534994289814532176e-04, /* 0x3F49EFE0, 0x7501B288 */
            pS5 =  3.47933107596021167570e-05, /* 0x3F023DE1, 0x0DFDF709 */
            qS1 = -2.40339491173441421878e+00, /* 0xC0033A27, 0x1C8A2D4B */
            qS2 =  2.02094576023350569471e+00, /* 0x40002AE5, 0x9C598AC8 */
            qS3 = -6.88283971605453293030e-01, /* 0xBFE6066C, 0x1B8D0159 */
            qS4 =  7.70381505559019352791e-02; /* 0x3FB3B8C5, 0xB12E9282 */

        static double compute(double x) {
            double z,p,q,r,w,s,c,df;
            int hx,ix;
            hx = __HI(x);
            ix = hx&0x7fffffff;
            if(ix>=0x3ff00000) {    /* |x| >= 1 */
                if(((ix-0x3ff00000)|__LO(x))==0) {  /* |x|==1 */
                    if(hx>0) return 0.0;            /* acos(1) = 0  */
                    else return pi+2.0*pio2_lo;     /* acos(-1)= pi */
                }
                return (x-x)/(x-x);         /* acos(|x|>1) is NaN */
            }
            if(ix<0x3fe00000) {     /* |x| < 0.5 */
                if(ix<=0x3c600000) return pio2_hi+pio2_lo;/*if|x|<2**-57*/
                z = x*x;
                p = z*(pS0+z*(pS1+z*(pS2+z*(pS3+z*(pS4+z*pS5)))));
                q = one+z*(qS1+z*(qS2+z*(qS3+z*qS4)));
                r = p/q;
                return pio2_hi - (x - (pio2_lo-x*r));
            } else  if (hx<0) {             /* x < -0.5 */
                z = (one+x)*0.5;
                p = z*(pS0+z*(pS1+z*(pS2+z*(pS3+z*(pS4+z*pS5)))));
                q = one+z*(qS1+z*(qS2+z*(qS3+z*qS4)));
                s = Math.sqrt(z);
                r = p/q;
                w = r*s-pio2_lo;
                return pi - 2.0*(s+w);
            } else {                        /* x > 0.5 */
                z = (one-x)*0.5;
                s = Math.sqrt(z);
                df = s;
                // __LO(df) = 0;
                df = __LO(df, 0);
                c  = (z-df*df)/(s+df);
                p = z*(pS0+z*(pS1+z*(pS2+z*(pS3+z*(pS4+z*pS5)))));
                q = one+z*(qS1+z*(qS2+z*(qS3+z*qS4)));
                r = p/q;
                w = r*s+c;
                return 2.0*(df+w);
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
    static class Atan {
        private static final double atanhi[] = {
            4.63647609000806093515e-01, /* atan(0.5)hi 0x3FDDAC67, 0x0561BB4F */
            7.85398163397448278999e-01, /* atan(1.0)hi 0x3FE921FB, 0x54442D18 */
            9.82793723247329054082e-01, /* atan(1.5)hi 0x3FEF730B, 0xD281F69B */
            1.57079632679489655800e+00, /* atan(inf)hi 0x3FF921FB, 0x54442D18 */
        };

        private static final double atanlo[] = {
            2.26987774529616870924e-17, /* atan(0.5)lo 0x3C7A2B7F, 0x222F65E2 */
            3.06161699786838301793e-17, /* atan(1.0)lo 0x3C81A626, 0x33145C07 */
            1.39033110312309984516e-17, /* atan(1.5)lo 0x3C700788, 0x7AF0CBBD */
            6.12323399573676603587e-17, /* atan(inf)lo 0x3C91A626, 0x33145C07 */
        };

        private static final double aT[] = {
             3.33333333333329318027e-01, /* 0x3FD55555, 0x5555550D */
            -1.99999999998764832476e-01, /* 0xBFC99999, 0x9998EBC4 */
             1.42857142725034663711e-01, /* 0x3FC24924, 0x920083FF */
            -1.11111104054623557880e-01, /* 0xBFBC71C6, 0xFE231671 */
             9.09088713343650656196e-02, /* 0x3FB745CD, 0xC54C206E */
            -7.69187620504482999495e-02, /* 0xBFB3B0F2, 0xAF749A6D */
             6.66107313738753120669e-02, /* 0x3FB10D66, 0xA0D03D51 */
            -5.83357013379057348645e-02, /* 0xBFADDE2D, 0x52DEFD9A */
             4.97687799461593236017e-02, /* 0x3FA97B4B, 0x24760DEB */
            -3.65315727442169155270e-02, /* 0xBFA2B444, 0x2C6A6C2F */
             1.62858201153657823623e-02, /* 0x3F90AD3A, 0xE322DA11 */
        };

        private static final double
            one   = 1.0,
            huge   = 1.0e300;

        static double compute(double x) {
            double w,s1,s2,z;
            int ix,hx,id;

            hx = __HI(x);
            ix = hx&0x7fffffff;
            if(ix>=0x44100000) {    /* if |x| >= 2^66 */
                if(ix>0x7ff00000||
                   (ix==0x7ff00000&&(__LO(x)!=0)))
                    return x+x;             /* NaN */
                if(hx>0) return  atanhi[3]+atanlo[3];
                else     return -atanhi[3]-atanlo[3];
            } if (ix < 0x3fdc0000) {        /* |x| < 0.4375 */
                if (ix < 0x3e200000) {      /* |x| < 2^-29 */
                    if(huge+x>one) return x;        /* raise inexact */
                }
                id = -1;
            } else {
                x = Math.abs(x);
                if (ix < 0x3ff30000) {          /* |x| < 1.1875 */
                    if (ix < 0x3fe60000) {      /* 7/16 <=|x|<11/16 */
                        id = 0; x = (2.0*x-one)/(2.0+x);
                    } else {                    /* 11/16<=|x|< 19/16 */
                        id = 1; x  = (x-one)/(x+one);
                    }
                } else {
                    if (ix < 0x40038000) {      /* |x| < 2.4375 */
                        id = 2; x  = (x-1.5)/(one+1.5*x);
                    } else {                    /* 2.4375 <= |x| < 2^66 */
                        id = 3; x  = -1.0/x;
                    }
                }}
            /* end of argument reduction */
            z = x*x;
            w = z*z;
            /* break sum from i=0 to 10 aT[i]z**(i+1) into odd and even poly */
            s1 = z*(aT[0]+w*(aT[2]+w*(aT[4]+w*(aT[6]+w*(aT[8]+w*aT[10])))));
            s2 = w*(aT[1]+w*(aT[3]+w*(aT[5]+w*(aT[7]+w*aT[9]))));
            if (id<0) return x - x*(s1+s2);
            else {
                z = atanhi[id] - ((x*(s1+s2) - atanlo[id]) - x);
                return (hx<0)? -z:z;
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
    static class Atan2 {
        private static final double
            tiny  = 1.0e-300,
            zero  = 0.0,
            pi_o_4  = 7.8539816339744827900E-01, /* 0x3FE921FB, 0x54442D18 */
            pi_o_2  = 1.5707963267948965580E+00, /* 0x3FF921FB, 0x54442D18 */
            pi      = 3.1415926535897931160E+00, /* 0x400921FB, 0x54442D18 */
            pi_lo   = 1.2246467991473531772E-16; /* 0x3CA1A626, 0x33145C07 */

        static double compute(double y, double x) {
            double z;
            int k,m,hx,hy,ix,iy;
            /*unsigned*/ int lx,ly;

            hx = __HI(x); ix = hx&0x7fffffff;
            lx = __LO(x);
            hy = __HI(y); iy = hy&0x7fffffff;
            ly = __LO(y);
            if(((ix|((lx|-lx)>>>31))>0x7ff00000)|| // Note unsigned shifts
               ((iy|((ly|-ly)>>>31))>0x7ff00000))    /* x or y is NaN */
                return x+y;
            if(((hx-0x3ff00000)|lx)==0) return atan(y);   /* x=1.0 */
            m = ((hy>>31)&1)|((hx>>30)&2);  /* 2*sign(x)+sign(y) */

            /* when y = 0 */
            if((iy|ly)==0) {
                switch(m) {
                case 0:
                case 1: return y;       /* atan(+-0,+anything)=+-0 */
                case 2: return  pi+tiny;/* atan(+0,-anything) = pi */
                case 3: return -pi-tiny;/* atan(-0,-anything) =-pi */
                }
            }
            /* when x = 0 */
            if((ix|lx)==0) return (hy<0)?  -pi_o_2-tiny: pi_o_2+tiny;

            /* when x is INF */
            if(ix==0x7ff00000) {
                if(iy==0x7ff00000) {
                    switch(m) {
                    case 0: return  pi_o_4+tiny;/* atan(+INF,+INF) */
                    case 1: return -pi_o_4-tiny;/* atan(-INF,+INF) */
                    case 2: return  3.0*pi_o_4+tiny;/*atan(+INF,-INF)*/
                    case 3: return -3.0*pi_o_4-tiny;/*atan(-INF,-INF)*/
                    }
                } else {
                    switch(m) {
                    case 0: return  zero  ;     /* atan(+...,+INF) */
                    case 1: return -1.0*zero  ; /* atan(-...,+INF) */
                    case 2: return  pi+tiny  ;  /* atan(+...,-INF) */
                    case 3: return -pi-tiny  ;  /* atan(-...,-INF) */
                    }
                }
            }
            /* when y is INF */
            if(iy==0x7ff00000) return (hy<0)? -pi_o_2-tiny: pi_o_2+tiny;

            /* compute y/x */
            k = (iy-ix)>>20;
            if(k > 60) z=pi_o_2+0.5*pi_lo;  /* |y/x| >  2**60 */
            else if(hx<0&&k<-60) z=0.0;     /* |y|/x < -2**60 */
            else z=atan(Math.abs(y/x));         /* safe to do y/x */
            switch (m) {
            case 0: return       z  ;   /* atan(+,+) */
            case 1:
                // original:__HI(z) ^= 0x80000000;
                z = __HI(z, __HI(z) ^ 0x80000000);
                return       z  ;   /* atan(-,+) */
            case 2: return  pi-(z-pi_lo);/* atan(+,-) */
            default: /* case 3 */
                return  (z-pi_lo)-pi;/* atan(-,-) */
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
    static class Sqrt {
        private static final double    one     = 1.0, tiny=1.0e-300;

        public static double compute(double x) {
            double z = 0.0;
            int     sign = (int)0x80000000;
            /*unsigned*/ int r,t1,s1,ix1,q1;
            int ix0,s0,q,m,t,i;

            ix0 = __HI(x);                  /* high word of x */
            ix1 = __LO(x);          /* low word of x */

            /* take care of Inf and NaN */
            if((ix0&0x7ff00000)==0x7ff00000) {
                return x*x+x;               /* sqrt(NaN)=NaN, sqrt(+inf)=+inf
                                               sqrt(-inf)=sNaN */
            }
            /* take care of zero */
            if(ix0<=0) {
                if(((ix0&(~sign))|ix1)==0) return x;/* sqrt(+-0) = +-0 */
                else if(ix0<0)
                    return (x-x)/(x-x);             /* sqrt(-ve) = sNaN */
            }
            /* normalize x */
            m = (ix0>>20);
            if(m==0) {                              /* subnormal x */
                while(ix0==0) {
                    m -= 21;
                    ix0 |= (ix1>>>11); ix1 <<= 21; // unsigned shift
                }
                for(i=0;(ix0&0x00100000)==0;i++) ix0<<=1;
                m -= i-1;
                ix0 |= (ix1>>>(32-i)); // unsigned shift
                ix1 <<= i;
            }
            m -= 1023;      /* unbias exponent */
            ix0 = (ix0&0x000fffff)|0x00100000;
            if((m&1) != 0){        /* odd m, double x to make it even */
                ix0 += ix0 + ((ix1&sign)>>>31); // unsigned shift
                ix1 += ix1;
            }
            m >>= 1;        /* m = [m/2] */

            /* generate sqrt(x) bit by bit */
            ix0 += ix0 + ((ix1&sign)>>>31); // unsigned shift
            ix1 += ix1;
            q = q1 = s0 = s1 = 0;   /* [q,q1] = sqrt(x) */
            r = 0x00200000;         /* r = moving bit from right to left */

            while(r!=0) {
                t = s0+r;
                if(t<=ix0) {
                    s0   = t+r;
                    ix0 -= t;
                    q   += r;
                }
                ix0 += ix0 + ((ix1&sign)>>>31); // unsigned shift
                ix1 += ix1;
                r>>>=1; // unsigned shift
            }

            r = sign;
            while(r!=0) {
                t1 = s1+r;
                t  = s0;
                if((t<ix0)||((t==ix0)&&(Integer.compareUnsigned(t1, ix1) <= 0 ))) { // t1<=ix1
                    s1  = t1+r;
                    if(((t1&sign)==sign)&&(s1&sign)==0) s0 += 1;
                    ix0 -= t;
                    if (Integer.compareUnsigned(ix1, t1) < 0) ix0 -= 1; // ix1 < t1
                    ix1 -= t1;
                    q1  += r;
                }
                ix0 += ix0 + ((ix1&sign)>>>31);
                ix1 += ix1;
                r>>>=1; // unsigned shift
            }

            /* use floating add to find out rounding direction */
            if((ix0|ix1)!=0) {
                z = one-tiny; /* trigger inexact flag */
                if (z>=one) {
                    z = one+tiny;
                    if (q1==0xffffffff) { q1=0; q += 1;}
                    else if (z>one) {
                        if (q1==0xfffffffe) q+=1;
                        q1+=2;
                    } else
                        q1 += (q1&1);
                }
            }
            ix0 = (q>>1)+0x3fe00000;
            ix1 =  q1>>>1; // unsigned shift
            if ((q&1)==1) ix1 |= sign;
            ix0 += (m <<20);
            // __HI(z) = ix0;
            z = __HI(z, ix0);
            // __LO(z) = ix1;
            z = __LO(z, ix1);
            return z;
        }
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
    private static final class Exp {
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

        static double compute(double x) {
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
     * Return the logarithm of x
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
    private static final class Log {
        private static final  double
            ln2_hi  =  6.93147180369123816490e-01,  /* 3fe62e42 fee00000 */
            ln2_lo  =  1.90821492927058770002e-10,  /* 3dea39ef 35793c76 */
            two54   =  1.80143985094819840000e+16,  /* 43500000 00000000 */
            Lg1 = 6.666666666666735130e-01,  /* 3FE55555 55555593 */
            Lg2 = 3.999999999940941908e-01,  /* 3FD99999 9997FA04 */
            Lg3 = 2.857142874366239149e-01,  /* 3FD24924 94229359 */
            Lg4 = 2.222219843214978396e-01,  /* 3FCC71C5 1D8E78AF */
            Lg5 = 1.818357216161805012e-01,  /* 3FC74664 96CB03DE */
            Lg6 = 1.531383769920937332e-01,  /* 3FC39A09 D078C69F */
            Lg7 = 1.479819860511658591e-01;  /* 3FC2F112 DF3E5244 */

        private static double zero   =  0.0;

        static double compute(double x) {
            double hfsq,f,s,z,R,w,t1,t2,dk;
            int k,hx,i,j;
            /*unsigned*/ int lx;

            hx = __HI(x);           /* high word of x */
            lx = __LO(x);           /* low  word of x */

            k=0;
            if (hx < 0x00100000) {                  /* x < 2**-1022  */
                if (((hx&0x7fffffff)|lx)==0)
                    return -two54/zero;             /* log(+-0)=-inf */
                if (hx<0) return (x-x)/zero;        /* log(-#) = NaN */
                k -= 54; x *= two54; /* subnormal number, scale up x */
                hx = __HI(x);               /* high word of x */
            }
            if (hx >= 0x7ff00000) return x+x;
            k += (hx>>20)-1023;
            hx &= 0x000fffff;
            i = (hx+0x95f64)&0x100000;
            // __HI(x) = hx|(i^0x3ff00000);    /* normalize x or x/2 */
            x =__HI(x, hx|(i^0x3ff00000));    /* normalize x or x/2 */
            k += (i>>20);
            f = x-1.0;
            if((0x000fffff&(2+hx))<3) {     /* |f| < 2**-20 */
                if(f==zero) {
                    if (k==0) return zero;
                    else {dk=(double)k; return dk*ln2_hi+dk*ln2_lo;}
                }
                R = f*f*(0.5-0.33333333333333333*f);
                if(k==0) return f-R; else {dk=(double)k;
                    return dk*ln2_hi-((R-dk*ln2_lo)-f);}
            }
            s = f/(2.0+f);
            dk = (double)k;
            z = s*s;
            i = hx-0x6147a;
            w = z*z;
            j = 0x6b851-hx;
            t1= w*(Lg2+w*(Lg4+w*Lg6));
            t2= z*(Lg1+w*(Lg3+w*(Lg5+w*Lg7)));
            i |= j;
            R = t2+t1;
            if(i>0) {
                hfsq=0.5*f*f;
                if(k==0) return f-(hfsq-s*(hfsq+R)); else
                    return dk*ln2_hi-((hfsq-(s*(hfsq+R)+dk*ln2_lo))-f);
            } else {
                if(k==0) return f-s*(f-R); else
                    return dk*ln2_hi-((s*(f-R)-dk*ln2_lo)-f);
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
            z  = y*log10_2lo + ivln10*log(x);
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
    private static final class Sinh {
        private static final double one = 1.0, shuge = 1.0e307;

        static double compute(double x) {
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
            if (ix < 0x40862E42) return h*FdlibmTranslit.exp(Math.abs(x));

            /* |x| in [log(maxdouble), overflowthresold] */
            // Note: the original FDLIBM sources use
            // lx = *( (((*(unsigned*)&one)>>29)) + (unsigned*)&x);
            // to set lx to the low-order 32 bits of x. The expression
            // in question is an alternate way to implement the
            // functionality of the C FDLIBM __LO macro and the
            // expression is coded to work on both big-edian and
            // little-endian machines. However, this port will instead
            // use the __LO method call to represent this
            // functionality.
            lx = __LO(x);
            if (ix<0x408633CE || ((ix==0x408633ce)&&(Long.compareUnsigned(lx, 0x8fb9f87d) <= 0 ))) {
                w = exp(0.5*Math.abs(x));
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
    private static final class Cosh {
        private static final double one = 1.0, half=0.5, huge = 1.0e300;
        static double compute(double x) {
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
                t = exp(Math.abs(x));
                return half*t+half/t;
            }

            /* |x| in [22, log(maxdouble)] return half*exp(|x|) */
            if (ix < 0x40862E42) return half*exp(Math.abs(x));

            /* |x| in [log(maxdouble), overflowthresold] */
            // See note above in the sinh implementation for how this
            // transliteration port uses __LO(x) in the line below
            // that differs from the idiom used in the original FDLIBM.
            lx = __LO(x);
            if (ix<0x408633CE ||
                ((ix==0x408633ce)&&(Integer.compareUnsigned(lx, 0x8fb9f87d) <= 0))) {
                w = exp(half*Math.abs(x));
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
    private static final class Tanh {
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
