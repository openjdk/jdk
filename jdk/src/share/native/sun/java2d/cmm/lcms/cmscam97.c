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


#include "lcms.h"


/*
typedef struct {
               double J;
               double C;
               double h;

               } cmsJCh, FAR* LPcmsJCh;


#define AVG_SURROUND_4     0
#define AVG_SURROUND       1
#define DIM_SURROUND       2
#define DARK_SURROUND      3
#define CUTSHEET_SURROUND  4


typedef struct {

              cmsCIEXYZ whitePoint;
              double    Yb;
              double    La;
              int       surround;
              double    D_value;

              } cmsViewingConditions, FAR* LPcmsViewingConditions;



LCMSAPI LCMSHANDLE LCMSEXPORT cmsCIECAM97sInit(LPcmsViewingConditions pVC);
LCMSAPI void   LCMSEXPORT cmsCIECAM97sDone(LCMSHANDLE hModel);
LCMSAPI void   LCMSEXPORT cmsCIECAM97sForward(LCMSHANDLE hModel, LPcmsCIEXYZ pIn, LPcmsJCh pOut);
LCMSAPI void   LCMSEXPORT cmsCIECAM97sReverse(LCMSHANDLE hModel, LPcmsJCh pIn,    LPcmsCIEXYZ pOut);

*/

// ---------- Implementation --------------------------------------------

// #define USE_CIECAM97s2  1

#ifdef USE_CIECAM97s2

#       define NOISE_CONSTANT   3.05
#else
#       define NOISE_CONSTANT   2.05
#endif


/*
  The model input data are the adapting field luminance in cd/m2
  (normally taken to be 20% of the luminance of white in the adapting field),
  LA , the relative tristimulus values of the stimulus, XYZ, the relative
  tristimulus values of white in the same viewing conditions, Xw Yw Zw ,
  and the relative luminance of the background, Yb . Relative tristimulus
  values should be expressed on a scale from Y = 0 for a perfect black
  to Y = 100 for a perfect reflecting diffuser. Additionally, the
  parameters c, for the impact of surround, Nc , a chromatic induction factor,
  and F, a factor for degree of adaptation, must be selected according to the
  guidelines in table

  All CIE tristimulus values are obtained using the CIE 1931
  Standard Colorimetric Observer (2°).

*/

typedef struct {

    cmsCIEXYZ WP;
    int surround;
    int calculate_D;

    double  Yb;         // rel. luminance of background

    cmsCIEXYZ RefWhite;

    double La;    // The adapting field luminance in cd/m2

    double c;     // Impact of surround
    double Nc;    // Chromatic induction factor
    double Fll;   // Lightness contrast factor (Removed on rev 2)
    double F;     // Degree of adaptation


    double k;
    double Fl;

    double Nbb;  // The background and chromatic brightness induction factors.
    double Ncb;
    double z;    // base exponential nonlinearity
    double n;    // background induction factor
    double D;

    MAT3 MlamRigg;
    MAT3 MlamRigg_1;

    MAT3 Mhunt;
    MAT3 Mhunt_1;

    MAT3 Mhunt_x_MlamRigg_1;
    MAT3 MlamRigg_x_Mhunt_1;


    VEC3 RGB_subw;
    VEC3 RGB_subw_prime;

    double p;

    VEC3 RGB_subwc;

    VEC3 RGB_subaw_prime;
    double A_subw;
    double Q_subw;

    } cmsCIECAM97s,FAR *LPcmsCIECAM97s;



// Free model structure

LCMSAPI void LCMSEXPORT cmsCIECAM97sDone(LCMSHANDLE hModel)
{
    LPcmsCIECAM97s lpMod = (LPcmsCIECAM97s) (LPSTR) hModel;
    if (lpMod) _cmsFree(lpMod);
}

// Partial discounting for adaptation degree computation

static
double discount(double d, double chan)
{
    return (d * chan + 1 - d);
}


// This routine does model exponential nonlinearity on the short wavelenght
// sensitive channel. On CIECAM97s rev 2 this has been reverted to linear.

static
void FwAdaptationDegree(LPcmsCIECAM97s lpMod, LPVEC3 RGBc, LPVEC3 RGB)
{


#ifdef USE_CIECAM97s2
    RGBc->n[0] = RGB->n[0]* discount(lpMod->D, 100.0/lpMod->RGB_subw.n[0]);
    RGBc->n[1] = RGB->n[1]* discount(lpMod->D, 100.0/lpMod->RGB_subw.n[1]);
    RGBc->n[2] = RGB->n[2]* discount(lpMod->D, 100.0/lpMod->RGB_subw.n[2]);
#else

    RGBc->n[0] = RGB->n[0]* discount(lpMod->D, 1.0/lpMod->RGB_subw.n[0]);
    RGBc->n[1] = RGB->n[1]* discount(lpMod->D, 1.0/lpMod->RGB_subw.n[1]);

    RGBc->n[2] = pow(fabs(RGB->n[2]), lpMod ->p) * discount(lpMod->D, (1.0/pow(lpMod->RGB_subw.n[2], lpMod->p)));

    // If B happens to be negative, Then Bc is also set to be negative

    if (RGB->n[2] < 0)
           RGBc->n[2] = -RGBc->n[2];
#endif
}


static
void RvAdaptationDegree(LPcmsCIECAM97s lpMod, LPVEC3 RGBc, LPVEC3 RGB)
{


#ifdef USE_CIECAM97s2
    RGBc->n[0] = RGB->n[0]/discount(lpMod->D, 100.0/lpMod->RGB_subw.n[0]);
    RGBc->n[1] = RGB->n[1]/discount(lpMod->D, 100.0/lpMod->RGB_subw.n[1]);
    RGBc->n[2] = RGB->n[2]/discount(lpMod->D, 100.0/lpMod->RGB_subw.n[2]);
#else

    RGBc->n[0] = RGB->n[0]/discount(lpMod->D, 1.0/lpMod->RGB_subw.n[0]);
    RGBc->n[1] = RGB->n[1]/discount(lpMod->D, 1.0/lpMod->RGB_subw.n[1]);
    RGBc->n[2] = pow(fabs(RGB->n[2]), 1.0/lpMod->p)/pow(discount(lpMod->D, 1.0/pow(lpMod->RGB_subw.n[2], lpMod->p)), 1.0/lpMod->p);
    if (RGB->n[2] < 0)
           RGBc->n[2] = -RGBc->n[2];
#endif
}



static
void PostAdaptationConeResponses(LPcmsCIECAM97s lpMod, LPVEC3 RGBa_prime, LPVEC3 RGBprime)
{
     if (RGBprime->n[0]>=0.0) {

            RGBa_prime->n[0]=((40.0*pow(lpMod -> Fl * RGBprime->n[0]/100.0, 0.73))/(pow(lpMod -> Fl * RGBprime->n[0]/100.0, 0.73)+2))+1;
     }
     else
     {
            RGBa_prime->n[0]=((-40.0*pow((-lpMod -> Fl * RGBprime->n[0])/100.0, 0.73))/(pow((-lpMod -> Fl * RGBprime->n[0])/100.0, 0.73)+2))+1;
     }

     if (RGBprime->n[1]>=0.0)
     {
            RGBa_prime->n[1]=((40.0*pow(lpMod -> Fl * RGBprime->n[1]/100.0, 0.73))/(pow(lpMod -> Fl * RGBprime->n[1]/100.0, 0.73)+2))+1;
     }
     else
     {
            RGBa_prime->n[1]=((-40.0*pow((-lpMod -> Fl * RGBprime->n[1])/100.0, 0.73))/(pow((-lpMod -> Fl * RGBprime->n[1])/100.0, 0.73)+2))+1;
     }

     if (RGBprime->n[2]>=0.0)
     {
            RGBa_prime->n[2]=((40.0*pow(lpMod -> Fl * RGBprime->n[2]/100.0, 0.73))/(pow(lpMod -> Fl * RGBprime->n[2]/100.0, 0.73)+2))+1;
     }
     else
     {
            RGBa_prime->n[2]=((-40.0*pow((-lpMod -> Fl * RGBprime->n[2])/100.0, 0.73))/(pow((-lpMod -> Fl * RGBprime->n[2])/100.0, 0.73)+2))+1;
     }
}


// Compute hue quadrature, eccentricity factor, e

static
void ComputeHueQuadrature(double h, double* H, double* e)
{


#define IRED    0
#define IYELLOW 1
#define IGREEN  2
#define IBLUE   3

      double e_tab[] = {0.8, 0.7, 1.0, 1.2};
      double H_tab[] = {  0, 100, 200, 300};
      int p1, p2;
      double e1, e2, h1, h2;


       if (h >= 20.14 && h < 90.0) { // Red

                        p1 = IRED;
                        p2 = IYELLOW;
       }
       else
       if (h >= 90.0 && h < 164.25) { // Yellow

                        p1 = IYELLOW;
                        p2 = IGREEN;
       }
       else
       if (h >= 164.25 && h < 237.53) { // Green

                        p1 = IGREEN;
                        p2 = IBLUE;       }
       else {                         // Blue

                        p1 = IBLUE;
                        p2 = IRED;
       }

       e1 = e_tab[p1]; e2 = e_tab[p2];
       h1 = H_tab[p1]; h2 = H_tab[p2];



       *e = e1 + ((e2-e1)*(h-h1)/(h2 - h1));
       *H = h1 + (100. * (h - h1) / e1) / ((h - h1)/e1 + (h2 - h) / e2);

#undef IRED
#undef IYELLOW
#undef IGREEN
#undef IBLUE

}






LCMSAPI LCMSHANDLE LCMSEXPORT cmsCIECAM97sInit(LPcmsViewingConditions pVC)
{
    LPcmsCIECAM97s lpMod;
    VEC3 tmp;

    if((lpMod = (LPcmsCIECAM97s) _cmsMalloc(sizeof(cmsCIECAM97s))) == NULL) {
        return (LCMSHANDLE) NULL;
    }


    lpMod->WP.X = pVC->whitePoint.X;
    lpMod->WP.Y = pVC->whitePoint.Y;
    lpMod->WP.Z = pVC->whitePoint.Z;

    lpMod->Yb   = pVC->Yb;
    lpMod->La   = pVC->La;

    lpMod->surround = pVC->surround;

    lpMod->RefWhite.X = 100.0;
    lpMod->RefWhite.Y = 100.0;
    lpMod->RefWhite.Z = 100.0;

#ifdef USE_CIECAM97s2

    VEC3init(&lpMod->MlamRigg.v[0],  0.8562, 0.3372, -0.1934);
    VEC3init(&lpMod->MlamRigg.v[1], -0.8360, 1.8327,  0.0033);
    VEC3init(&lpMod->MlamRigg.v[2],  0.0357,-0.0469,  1.0112);

    VEC3init(&lpMod->MlamRigg_1.v[0], 0.9874, -0.1768, 0.1894);
    VEC3init(&lpMod->MlamRigg_1.v[1], 0.4504,  0.4649, 0.0846);
    VEC3init(&lpMod->MlamRigg_1.v[2],-0.0139,  0.0278, 0.9861);

#else
    // Bradford transform: Lam-Rigg cone responses
    VEC3init(&lpMod->MlamRigg.v[0],  0.8951,  0.2664, -0.1614);
    VEC3init(&lpMod->MlamRigg.v[1], -0.7502,  1.7135,  0.0367);
    VEC3init(&lpMod->MlamRigg.v[2],  0.0389, -0.0685,  1.0296);


    // Inverse of Lam-Rigg
    VEC3init(&lpMod->MlamRigg_1.v[0],  0.98699, -0.14705,  0.15996);
    VEC3init(&lpMod->MlamRigg_1.v[1],  0.43231,  0.51836,  0.04929);
    VEC3init(&lpMod->MlamRigg_1.v[2], -0.00853,  0.04004,  0.96849);

#endif

    // Hunt-Pointer-Estevez cone responses
    VEC3init(&lpMod->Mhunt.v[0],   0.38971,  0.68898, -0.07868);
    VEC3init(&lpMod->Mhunt.v[1],  -0.22981,  1.18340,  0.04641);
    VEC3init(&lpMod->Mhunt.v[2],   0.0,      0.0,      1.0);

    // Inverse of Hunt-Pointer-Estevez
    VEC3init(&lpMod->Mhunt_1.v[0],     1.91019, -1.11214, 0.20195);
    VEC3init(&lpMod->Mhunt_1.v[1],     0.37095,  0.62905, 0.0);
    VEC3init(&lpMod->Mhunt_1.v[2],     0.0,      0.0,     1.0);


    if (pVC->D_value == -1.0)
          lpMod->calculate_D = 1;
    else
    if (pVC->D_value == -2.0)
           lpMod->calculate_D = 2;
    else {
        lpMod->calculate_D = 0;
        lpMod->D = pVC->D_value;
    }

   // Table I (revised)

   switch (lpMod->surround) {

    case AVG_SURROUND_4:
       lpMod->F = 1.0;
       lpMod->c = 0.69;
       lpMod->Fll = 0.0;    // Not included on Rev 2
       lpMod->Nc = 1.0;
       break;
    case AVG_SURROUND:
       lpMod->F = 1.0;
       lpMod->c = 0.69;
       lpMod->Fll = 1.0;
       lpMod->Nc = 1.0;
       break;
    case DIM_SURROUND:
       lpMod->F = 0.99;
       lpMod->c = 0.59;
       lpMod->Fll = 1.0;
       lpMod->Nc = 0.95;
       break;
    case DARK_SURROUND:
       lpMod->F = 0.9;
       lpMod->c = 0.525;
       lpMod->Fll = 1.0;
       lpMod->Nc = 0.8;
       break;
    case CUTSHEET_SURROUND:
       lpMod->F = 0.9;
       lpMod->c = 0.41;
       lpMod->Fll = 1.0;
       lpMod->Nc = 0.8;
       break;
    default:
       lpMod->F = 1.0;
       lpMod->c = 0.69;
       lpMod->Fll = 1.0;
       lpMod->Nc = 1.0;
       break;
    }

    lpMod->k = 1 / (5 * lpMod->La  + 1);
    lpMod->Fl = lpMod->La * pow(lpMod->k, 4) + 0.1*pow(1 - pow(lpMod->k, 4), 2.0) * pow(5*lpMod->La, 1.0/3.0);

    if (lpMod->calculate_D > 0) {

       lpMod->D = lpMod->F * (1 - 1 / (1 + 2*pow(lpMod->La, 0.25) + pow(lpMod->La, 2)/300.0));
       if (lpMod->calculate_D > 1)
           lpMod->D = (lpMod->D + 1.0) / 2;
    }


    // RGB_subw = [MlamRigg][WP/YWp]
#ifdef USE_CIECAM97s2
    MAT3eval(&lpMod -> RGB_subw, &lpMod -> MlamRigg, &lpMod -> WP);
#else
    VEC3divK(&tmp, (LPVEC3) &lpMod -> WP, lpMod->WP.Y);
    MAT3eval(&lpMod -> RGB_subw, &lpMod -> MlamRigg, &tmp);
#endif



    MAT3per(&lpMod -> Mhunt_x_MlamRigg_1,   &lpMod -> Mhunt,   &lpMod->MlamRigg_1  );
    MAT3per(&lpMod -> MlamRigg_x_Mhunt_1,   &lpMod -> MlamRigg, &lpMod -> Mhunt_1  );

    // p is used on forward model
    lpMod->p = pow(lpMod->RGB_subw.n[2], 0.0834);

    FwAdaptationDegree(lpMod, &lpMod->RGB_subwc, &lpMod->RGB_subw);

#if USE_CIECAM97s2
    MAT3eval(&lpMod->RGB_subw_prime, &lpMod->Mhunt_x_MlamRigg_1, &lpMod -> RGB_subwc);
#else
    VEC3perK(&tmp, &lpMod -> RGB_subwc, lpMod->WP.Y);
    MAT3eval(&lpMod->RGB_subw_prime, &lpMod->Mhunt_x_MlamRigg_1, &tmp);
#endif

    lpMod->n = lpMod-> Yb / lpMod-> WP.Y;

    lpMod->z = 1 + lpMod->Fll * sqrt(lpMod->n);
    lpMod->Nbb = lpMod->Ncb = 0.725 / pow(lpMod->n, 0.2);

    PostAdaptationConeResponses(lpMod, &lpMod->RGB_subaw_prime, &lpMod->RGB_subw_prime);

    lpMod->A_subw=lpMod->Nbb*(2.0*lpMod->RGB_subaw_prime.n[0]+lpMod->RGB_subaw_prime.n[1]+lpMod->RGB_subaw_prime.n[2]/20.0-NOISE_CONSTANT);

    return (LCMSHANDLE) lpMod;
}




//
// The forward model: XYZ -> JCh
//

LCMSAPI void LCMSEXPORT cmsCIECAM97sForward(LCMSHANDLE hModel, LPcmsCIEXYZ inPtr, LPcmsJCh outPtr)
{

        LPcmsCIECAM97s lpMod = (LPcmsCIECAM97s) (LPSTR) hModel;
        double a, b, h, s, H1val, es, A;
        VEC3 In, RGB, RGBc, RGBprime, RGBa_prime;

        if (inPtr -> Y <= 0.0) {

      outPtr -> J = outPtr -> C = outPtr -> h = 0.0;
          return;
        }

       // An initial chromatic adaptation transform is used to go from the source
       // viewing conditions to corresponding colours under the equal-energy-illuminant
       // reference viewing conditions. This is handled differently on rev 2

       VEC3init(&In, inPtr -> X, inPtr -> Y, inPtr -> Z);    // 2.1

#ifdef USE_CIECAM97s2
       // Since the chromatic adaptation transform has been linearized, it
       // is no longer required to divide the stimulus tristimulus values
       // by their own Y tristimulus value prior to the chromatic adaptation.
#else
       VEC3divK(&In, &In, inPtr -> Y);
#endif

       MAT3eval(&RGB, &lpMod -> MlamRigg, &In);              // 2.2

       FwAdaptationDegree(lpMod, &RGBc, &RGB);

       // The post-adaptation signals for both the sample and the white are then
       // transformed from the sharpened cone responses to the Hunt-Pointer-Estevez
       // cone responses.
#ifdef USE_CIECAM97s2
#else
       VEC3perK(&RGBc, &RGBc, inPtr->Y);
#endif

       MAT3eval(&RGBprime, &lpMod->Mhunt_x_MlamRigg_1, &RGBc);

       // The post-adaptation cone responses (for both the stimulus and the white)
       // are then calculated.

       PostAdaptationConeResponses(lpMod, &RGBa_prime, &RGBprime);

       // Preliminary red-green and yellow-blue opponent dimensions are calculated

       a = RGBa_prime.n[0] - (12.0 * RGBa_prime.n[1] / 11.0) + RGBa_prime.n[2]/11.0;
       b = (RGBa_prime.n[0] + RGBa_prime.n[1] - 2.0 * RGBa_prime.n[2]) / 9.0;


       // The CIECAM97s hue angle, h, is then calculated
       h = (180.0/M_PI)*(atan2(b, a));


       while (h < 0)
              h += 360.0;

       outPtr->h = h;

       // hue quadrature and eccentricity factors, e, are calculated

       ComputeHueQuadrature(h, &H1val, &es);

       // ComputeHueQuadrature(h, &H1val, &h1, &e1, &h2, &e2, &es);


      // The achromatic response A
      A = lpMod->Nbb * (2.0 * RGBa_prime.n[0] + RGBa_prime.n[1] + RGBa_prime.n[2]/20.0 - NOISE_CONSTANT);

      // CIECAM97s Lightness J
      outPtr -> J = 100.0 * pow(A / lpMod->A_subw, lpMod->c * lpMod->z);

      // CIECAM97s saturation s
      s =  (50 * hypot (a, b) * 100 * es * (10.0/13.0) * lpMod-> Nc * lpMod->Ncb) / (RGBa_prime.n[0] + RGBa_prime.n[1] + 1.05 * RGBa_prime.n[2]);

      // CIECAM97s Chroma C

#ifdef USE_CIECAM97s2
      // Eq. 26 has been modified to allow accurate prediction of the Munsell chroma scales.
      outPtr->C = 0.7487 * pow(s, 0.973) * pow(outPtr->J/100.0, 0.945 * lpMod->n) * (1.64 - pow(0.29, lpMod->n));

#else
      outPtr->C = 2.44 * pow(s, 0.69) * pow(outPtr->J/100.0, 0.67 * lpMod->n) * (1.64 - pow(0.29, lpMod->n));
#endif
}


//
// The reverse model JCh -> XYZ
//


LCMSAPI void LCMSEXPORT cmsCIECAM97sReverse(LCMSHANDLE hModel, LPcmsJCh inPtr, LPcmsCIEXYZ outPtr)
{
    LPcmsCIECAM97s lpMod = (LPcmsCIECAM97s) (LPSTR) hModel;
    double J, C, h, A, H1val, es, s, a, b;
    double tan_h, sec_h;
    double R_suba_prime, G_suba_prime, B_suba_prime;
    double R_prime, G_prime, B_prime;
    double Y_subc, Y_prime, B_term;
    VEC3 tmp;
    VEC3 RGB_prime, RGB_subc_Y;
    VEC3 Y_over_Y_subc_RGB;
    VEC3 XYZ_primeprime_over_Y_subc;
#ifdef USE_CIECAM92s2
    VEC3 RGBY;
    VEC3 Out;
#endif

    J = inPtr->J;
    h = inPtr->h;
    C = inPtr->C;

    if (J <= 0) {

        outPtr->X =  0.0;
        outPtr->Y =  0.0;
        outPtr->Z =  0.0;
        return;
    }



    // (2) From J Obtain A

    A =  pow(J/100.0, 1/(lpMod->c * lpMod->z)) * lpMod->A_subw;


    // (3), (4), (5) Using H Determine h1, h2, e1, e2
    // e1 and h1 are the values  of e and h for the unique hue having the
    // nearest lower valur of h and e2 and h2 are the values of e and h for
    // the unique hue having the nearest higher value of h.


    ComputeHueQuadrature(h, &H1val, &es);

    // (7) Calculate s

    s = pow(C / (2.44 * pow(J/100.0, 0.67*lpMod->n) * (1.64 - pow(0.29, lpMod->n))) , (1./0.69));


    // (8) Calculate a and b.
    // NOTE: sqrt(1 + tan^2) == sec(h)

    tan_h = tan ((M_PI/180.)*(h));
    sec_h = sqrt(1 + tan_h * tan_h);

    if ((h > 90) && (h < 270))
            sec_h = -sec_h;

    a = s * ( A/lpMod->Nbb + NOISE_CONSTANT) / ( sec_h * 50000.0 * es * lpMod->Nc * lpMod->Ncb/ 13.0 +
           s * (11.0 / 23.0 + (108.0/23.0) * tan_h));

    b = a * tan_h;

    //(9) Calculate R'a G'a and B'a

    R_suba_prime = (20.0/61.0) * (A/lpMod->Nbb + NOISE_CONSTANT) + (41.0/61.0) * (11.0/23.0) * a + (288.0/61.0) / 23.0 * b;
    G_suba_prime = (20.0/61.0) * (A/lpMod->Nbb + NOISE_CONSTANT) - (81.0/61.0) * (11.0/23.0) * a - (261.0/61.0) / 23.0 * b;
    B_suba_prime = (20.0/61.0) * (A/lpMod->Nbb + NOISE_CONSTANT) - (20.0/61.0) * (11.0/23.0) * a - (20.0/61.0) * (315.0/23.0) * b;

    // (10) Calculate R', G' and B'

    if ((R_suba_prime - 1) < 0) {

         R_prime = -100.0 * pow((2.0 - 2.0 * R_suba_prime) /
                            (39.0 + R_suba_prime), 1.0/0.73);
    }
    else
    {
         R_prime = 100.0 * pow((2.0 * R_suba_prime - 2.0) /
                            (41.0 - R_suba_prime), 1.0/0.73);
    }

    if ((G_suba_prime - 1) < 0)
    {
         G_prime = -100.0 * pow((2.0 - 2.0 * G_suba_prime) /
                            (39.0 + G_suba_prime), 1.0/0.73);
    }
    else
    {
         G_prime = 100.0 * pow((2.0 * G_suba_prime - 2.0) /
                            (41.0 - G_suba_prime), 1.0/0.73);
    }

    if ((B_suba_prime - 1) < 0)
    {
         B_prime = -100.0 * pow((2.0 - 2.0 * B_suba_prime) /
                            (39.0 + B_suba_prime), 1.0/0.73);
    }
    else
    {
         B_prime = 100.0 * pow((2.0 * B_suba_prime - 2.0) /
                            (41.0 - B_suba_prime), 1.0/0.73);
    }


    // (11) Calculate RcY, GcY and BcY

    VEC3init(&RGB_prime, R_prime, G_prime, B_prime);
    VEC3divK(&tmp, &RGB_prime, lpMod -> Fl);

    MAT3eval(&RGB_subc_Y, &lpMod->MlamRigg_x_Mhunt_1, &tmp);




#ifdef USE_CIECAM97s2

       // (12)


           RvAdaptationDegree(lpMod, &RGBY, &RGB_subc_Y);
           MAT3eval(&Out, &lpMod->MlamRigg_1, &RGBY);

           outPtr -> X = Out.n[0];
           outPtr -> Y = Out.n[1];
           outPtr -> Z = Out.n[2];

#else

           // (12) Calculate Yc

       Y_subc = 0.43231*RGB_subc_Y.n[0]+0.51836*RGB_subc_Y.n[1]+0.04929*RGB_subc_Y.n[2];

           // (13) Calculate (Y/Yc)R, (Y/Yc)G and (Y/Yc)B

           VEC3divK(&RGB_subc_Y, &RGB_subc_Y, Y_subc);
           RvAdaptationDegree(lpMod, &Y_over_Y_subc_RGB, &RGB_subc_Y);

           // (14) Calculate Y'
       Y_prime = 0.43231*(Y_over_Y_subc_RGB.n[0]*Y_subc) + 0.51836*(Y_over_Y_subc_RGB.n[1]*Y_subc) + 0.04929 * (Y_over_Y_subc_RGB.n[2]*Y_subc);

           if (Y_prime < 0 || Y_subc < 0)
           {
                // Discard to near black point

                outPtr -> X = 0;
                outPtr -> Y = 0;
                outPtr -> Z = 0;
                return;
           }

       B_term = pow(Y_prime / Y_subc, (1.0 / lpMod->p) - 1);

          // (15) Calculate X'', Y'' and Z''
           Y_over_Y_subc_RGB.n[2] /= B_term;
           MAT3eval(&XYZ_primeprime_over_Y_subc, &lpMod->MlamRigg_1, &Y_over_Y_subc_RGB);

           outPtr->X =  XYZ_primeprime_over_Y_subc.n[0] * Y_subc;
           outPtr->Y =  XYZ_primeprime_over_Y_subc.n[1] * Y_subc;
           outPtr->Z =  XYZ_primeprime_over_Y_subc.n[2] * Y_subc;
#endif

}
