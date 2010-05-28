/*
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

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
//
//  Little cms
//  Copyright (C) 1998-2007 Marti Maria
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software
// is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

//      inter PCS conversions XYZ <-> CIE L* a* b*

#include "lcms.h"

/*


       CIE 15:2004 CIELab is defined as:

       L* = 116*f(Y/Yn) - 16                     0 <= L* <= 100
       a* = 500*[f(X/Xn) - f(Y/Yn)]
       b* = 200*[f(Y/Yn) - f(Z/Zn)]

       and

              f(t) = t^(1/3)                     1 >= t >  (24/116)^3
                     (841/108)*t + (16/116)      0 <= t <= (24/116)^3


       Reverse transform is:

       X = Xn*[a* / 500 + (L* + 16) / 116] ^ 3   if (X/Xn) > (24/116)
         = Xn*(a* / 500 + L* / 116) / 7.787      if (X/Xn) <= (24/116)



       Following ICC. PCS in Lab is coded as:

              8 bit Lab PCS:

                     L*      0..100 into a 0..ff byte.
                     a*      t + 128 range is -128.0  +127.0
                     b*

             16 bit Lab PCS:

                     L*     0..100  into a 0..ff00 word.
                     a*     t + 128  range is  -128.0  +127.9961
                     b*


       We are always playing with 16 bits-data, so I will ignore the
       8-bits encoding scheme.


Interchange Space   Component     Actual Range        Encoded Range
CIE XYZ             X             0 -> 1.99997        0x0000 -> 0xffff
CIE XYZ             Y             0 -> 1.99997        0x0000 -> 0xffff
CIE XYZ             Z             0 -> 1.99997        0x0000 -> 0xffff

Version 2,3
-----------

CIELAB (16 bit)     L*            0 -> 100.0          0x0000 -> 0xff00
CIELAB (16 bit)     a*            -128.0 -> +127.996  0x0000 -> 0x8000 -> 0xffff
CIELAB (16 bit)     b*            -128.0 -> +127.996  0x0000 -> 0x8000 -> 0xffff


Version 4
---------

CIELAB (16 bit)     L*            0 -> 100.0          0x0000 -> 0xffff
CIELAB (16 bit)     a*            -128.0 -> +127      0x0000 -> 0x8080 -> 0xffff
CIELAB (16 bit)     b*            -128.0 -> +127      0x0000 -> 0x8080 -> 0xffff

*/




// On most modern computers, D > 4 M (i.e. a division takes more than 4
// multiplications worth of time), so it is probably preferable to compute
// a 24 bit result directly.

// #define ITERATE 1

static
float CubeRoot(float x)
{
       float fr, r;
       int ex, shx;

       /* Argument reduction */
       fr = (float) frexp(x, &ex); /* separate into mantissa and exponent */
       shx = ex % 3;

       if (shx > 0)
              shx -= 3; /* compute shx such that (ex - shx) is divisible by 3 */

       ex = (ex - shx) / 3;        /* exponent of cube root */
       fr = (float) ldexp(fr, shx);

       /* 0.125 <= fr < 1.0 */

#ifdef ITERATE
       /* Compute seed with a quadratic approximation */

       fr = (-0.46946116F * fr + 1.072302F) * fr + 0.3812513F;/* 0.5<=fr<1 */
       r = ldexp(fr, ex);          /* 6 bits of precision */

       /* Newton-Raphson iterations */

       r = (float)(2.0/3.0) * r + (float)(1.0/3.0) * x / (r * r); /* 12 bits */
       r = (float)(2.0/3.0) * r + (float)(1.0/3.0) * x / (r * r); /* 24 bits */
#else /* ITERATE */

       /* Use quartic rational polynomial with error < 2^(-24) */

       fr = (float) (((((45.2548339756803022511987494 * fr +
       192.2798368355061050458134625) * fr +
       119.1654824285581628956914143) * fr +
       13.43250139086239872172837314) * fr +
       0.1636161226585754240958355063)
       /
       ((((14.80884093219134573786480845 * fr +
       151.9714051044435648658557668) * fr +
       168.5254414101568283957668343) * fr +
       33.9905941350215598754191872) * fr +
       1.0));
       r = (float) ldexp(fr, ex); /* 24 bits of precision */
#endif
       return r;
}

static
double f(double t)
{

      const double Limit = (24.0/116.0) * (24.0/116.0) * (24.0/116.0);

       if (t <= Limit)
              return (841.0/108.0) * t + (16.0/116.0);
       else
              return CubeRoot((float) t);
}


static
double f_1(double t)
{
       const double Limit = (24.0/116.0);

       if (t <= Limit)
       {
              double tmp;

              tmp = (108.0/841.0) * (t - (16.0/116.0));
              if (tmp <= 0.0) return 0.0;
              else return tmp;
       }

       return t * t * t;
}



void LCMSEXPORT cmsXYZ2Lab(LPcmsCIEXYZ WhitePoint, LPcmsCIELab Lab, const cmsCIEXYZ* xyz)
{
       double fx, fy, fz;

       if (xyz -> X == 0 && xyz -> Y == 0 && xyz -> Z == 0)
       {
        Lab -> L = 0;
        Lab -> a = 0;
        Lab -> b = 0;
        return;
       }

       if (WhitePoint == NULL)
            WhitePoint = cmsD50_XYZ();

       fx = f(xyz->X / WhitePoint->X);
       fy = f(xyz->Y / WhitePoint->Y);
       fz = f(xyz->Z / WhitePoint->Z);

       Lab->L = 116.0* fy - 16.;

       Lab->a = 500.0*(fx - fy);
       Lab->b = 200.0*(fy - fz);
}



void cmsXYZ2LabEncoded(WORD XYZ[3], WORD Lab[3])
{
       Fixed32 X, Y, Z;
       double x, y, z, L, a, b;
       double fx, fy, fz;
       Fixed32 wL, wa, wb;

       X = (Fixed32) XYZ[0] << 1;
       Y = (Fixed32) XYZ[1] << 1;
       Z = (Fixed32) XYZ[2] << 1;


       if (X==0 && Y==0 && Z==0) {

                     Lab[0] = 0;
                     Lab[1] = Lab[2] =  0x8000;
                     return;
       }

       // PCS is in D50


       x = FIXED_TO_DOUBLE(X) / D50X;
       y = FIXED_TO_DOUBLE(Y) / D50Y;
       z = FIXED_TO_DOUBLE(Z) / D50Z;


       fx = f(x);
       fy = f(y);
       fz = f(z);

       L = 116.* fy - 16.;

       a = 500.*(fx - fy);
       b = 200.*(fy - fz);

       a += 128.;
       b += 128.;

       wL = (int) (L * 652.800 + .5);
       wa = (int) (a * 256.0   + .5);
       wb = (int) (b * 256.0   + .5);


       Lab[0] = Clamp_L(wL);
       Lab[1] = Clamp_ab(wa);
       Lab[2] = Clamp_ab(wb);


}






void LCMSEXPORT cmsLab2XYZ(LPcmsCIEXYZ WhitePoint, LPcmsCIEXYZ xyz,  const cmsCIELab* Lab)
{
        double x, y, z;

        if (Lab -> L <= 0) {
               xyz -> X = 0;
               xyz -> Y = 0;
               xyz -> Z = 0;
               return;
        }


       if (WhitePoint == NULL)
            WhitePoint = cmsD50_XYZ();

       y = (Lab-> L + 16.0) / 116.0;
       x = y + 0.002 * Lab -> a;
       z = y - 0.005 * Lab -> b;

       xyz -> X = f_1(x) * WhitePoint -> X;
       xyz -> Y = f_1(y) * WhitePoint -> Y;
       xyz -> Z = f_1(z) * WhitePoint -> Z;

}



void cmsLab2XYZEncoded(WORD Lab[3], WORD XYZ[3])
{
       double L, a, b;
       double X, Y, Z, x, y, z;


       L = ((double) Lab[0] * 100.0) / 65280.0;
       if (L==0.0) {

       XYZ[0] = 0; XYZ[1] = 0; XYZ[2] = 0;
       return;
       }

       a = ((double) Lab[1] / 256.0) - 128.0;
       b = ((double) Lab[2] / 256.0) - 128.0;

       y = (L + 16.) / 116.0;
       x = y + 0.002 * a;
       z = y - 0.005 * b;

       X = f_1(x) * D50X;
       Y = f_1(y) * D50Y;
       Z = f_1(z) * D50Z;

       // Convert to 1.15 fixed format PCS


       XYZ[0] = _cmsClampWord((int) floor(X * 32768.0 + 0.5));
       XYZ[1] = _cmsClampWord((int) floor(Y * 32768.0 + 0.5));
       XYZ[2] = _cmsClampWord((int) floor(Z * 32768.0 + 0.5));


}

static
double L2float3(WORD v)
{
       Fixed32 fix32;

       fix32 = (Fixed32) v;
       return (double) fix32 / 652.800;
}


// the a/b part

static
double ab2float3(WORD v)
{
       Fixed32 fix32;

       fix32 = (Fixed32) v;
       return ((double) fix32/256.0)-128.0;
}

static
WORD L2Fix3(double L)
{
        return (WORD) (L *  652.800 + 0.5);
}

static
WORD ab2Fix3(double ab)
{
        return (WORD) ((ab + 128.0) * 256.0 + 0.5);
}


// ICC 4.0 -- ICC has changed PCS Lab encoding.

static
WORD L2Fix4(double L)
{
     return (WORD) (L *  655.35 + 0.5);
}

static
WORD ab2Fix4(double ab)
{
        return (WORD) ((ab + 128.0) * 257.0 + 0.5);
}

static
double L2float4(WORD v)
{
       Fixed32 fix32;

       fix32 = (Fixed32) v;
       return (double) fix32 / 655.35;
}


// the a/b part

static
double ab2float4(WORD v)
{
       Fixed32 fix32;

       fix32 = (Fixed32) v;
       return ((double) fix32/257.0)-128.0;
}


void LCMSEXPORT cmsLabEncoded2Float(LPcmsCIELab Lab, const WORD wLab[3])
{
        Lab->L = L2float3(wLab[0]);
        Lab->a = ab2float3(wLab[1]);
        Lab->b = ab2float3(wLab[2]);
}


void LCMSEXPORT cmsLabEncoded2Float4(LPcmsCIELab Lab, const WORD wLab[3])
{
        Lab->L = L2float4(wLab[0]);
        Lab->a = ab2float4(wLab[1]);
        Lab->b = ab2float4(wLab[2]);
}

static
double Clamp_L_double(double L)
{
    if (L < 0) L = 0;
    if (L > 100) L = 100;

    return L;
}


static
double Clamp_ab_double(double ab)
{
    if (ab < -128) ab = -128.0;
    if (ab > +127.9961) ab = +127.9961;

    return ab;
}

void LCMSEXPORT cmsFloat2LabEncoded(WORD wLab[3], const cmsCIELab* fLab)
{
    cmsCIELab Lab;


    Lab.L = Clamp_L_double(fLab ->L);
    Lab.a = Clamp_ab_double(fLab ->a);
    Lab.b = Clamp_ab_double(fLab ->b);

    wLab[0] = L2Fix3(Lab.L);
    wLab[1] = ab2Fix3(Lab.a);
    wLab[2] = ab2Fix3(Lab.b);
}


void LCMSEXPORT cmsFloat2LabEncoded4(WORD wLab[3], const cmsCIELab* fLab)
{
    cmsCIELab Lab;


    Lab.L = fLab ->L;
    Lab.a = fLab ->a;
    Lab.b = fLab ->b;


    if (Lab.L < 0) Lab.L = 0;
    if (Lab.L > 100.) Lab.L = 100.;

    if (Lab.a < -128.) Lab.a = -128.;
    if (Lab.a > 127.) Lab.a = 127.;
    if (Lab.b < -128.) Lab.b = -128.;
    if (Lab.b > 127.) Lab.b = 127.;


    wLab[0] = L2Fix4(Lab.L);
    wLab[1] = ab2Fix4(Lab.a);
    wLab[2] = ab2Fix4(Lab.b);
}




void LCMSEXPORT cmsLab2LCh(LPcmsCIELCh LCh, const cmsCIELab* Lab)
{
    double a, b;

    LCh -> L = Clamp_L_double(Lab -> L);

    a = Clamp_ab_double(Lab -> a);
    b = Clamp_ab_double(Lab -> b);

    LCh -> C = pow(a * a + b * b, 0.5);

    if (a == 0 && b == 0)
            LCh -> h   = 0;
    else
            LCh -> h = atan2(b, a);


    LCh -> h *= (180. / M_PI);


    while (LCh -> h >= 360.)         // Not necessary, but included as a check.
                LCh -> h -= 360.;

    while (LCh -> h < 0)
                LCh -> h += 360.;

}




void LCMSEXPORT cmsLCh2Lab(LPcmsCIELab Lab, const cmsCIELCh* LCh)
{

    double h = (LCh -> h * M_PI) / 180.0;

    Lab -> L = Clamp_L_double(LCh -> L);
    Lab -> a = Clamp_ab_double(LCh -> C * cos(h));
    Lab -> b = Clamp_ab_double(LCh -> C * sin(h));

}





// In XYZ All 3 components are encoded using 1.15 fixed point

static
WORD XYZ2Fix(double d)
{
    return (WORD) floor(d * 32768.0 + 0.5);
}


void LCMSEXPORT cmsFloat2XYZEncoded(WORD XYZ[3], const cmsCIEXYZ* fXYZ)
{
    cmsCIEXYZ xyz;

    xyz.X = fXYZ -> X;
    xyz.Y = fXYZ -> Y;
    xyz.Z = fXYZ -> Z;


    // Clamp to encodeable values.
    // 1.99997 is reserved as out-of-gamut marker


    if (xyz.Y <= 0) {

                xyz.X = 0;
                xyz.Y = 0;
                xyz.Z = 0;
    }


    if (xyz.X > 1.99996)
           xyz.X = 1.99996;

    if (xyz.X < 0)
           xyz.X = 0;

    if (xyz.Y > 1.99996)
                xyz.Y = 1.99996;

    if (xyz.Y < 0)
           xyz.Y = 0;


    if (xyz.Z > 1.99996)
                xyz.Z = 1.99996;

    if (xyz.Z < 0)
           xyz.Z = 0;



    XYZ[0] = XYZ2Fix(xyz.X);
    XYZ[1] = XYZ2Fix(xyz.Y);
    XYZ[2] = XYZ2Fix(xyz.Z);

}


//  To convert from Fixed 1.15 point to double

static
double XYZ2float(WORD v)
{
       Fixed32 fix32;

       // From 1.15 to 15.16

       fix32 = v << 1;

       // From fixed 15.16 to double

       return FIXED_TO_DOUBLE(fix32);
}


void LCMSEXPORT cmsXYZEncoded2Float(LPcmsCIEXYZ fXYZ, const WORD XYZ[3])
{

    fXYZ -> X = XYZ2float(XYZ[0]);
    fXYZ -> Y = XYZ2float(XYZ[1]);
    fXYZ -> Z = XYZ2float(XYZ[2]);

}




