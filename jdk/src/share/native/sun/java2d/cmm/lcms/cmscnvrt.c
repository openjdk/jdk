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
       This module provides conversion stages for handling intents.

The chain of evaluation in a transform is:

                PCS1            PCS2                    PCS3          PCS4

|From |  |From  |  |Conversion |  |Preview |  |Gamut   |  |Conversion |  |To    |  |To     |
|Input|->|Device|->|Stage 1    |->|handling|->|Checking|->|Stage 2    |->|Device|->|output |

--------  -------  -------------   ---------  ----------  -------------   -------  ---------

          AToB0                     prew0       gamut                     BToA0
Formatting LUT      Adjusting        LUT         LUT       Adjusting       LUT      Formatting
          Intent     Intent 1       intent      intent      Intent 2      Intent


Some of these LUT may be missing

There are two intents involved here, the intent of the transform itself, and the
intent the proof is being done, if is the case. Since the first intent is to be
applied to preview, is the proofing intent. The second intent  identifies the
transform intent. Input data of any stage is taked as relative colorimetric
always.


NOTES: V4 states than perceptual & saturation intents between mixed v2 & v4 profiles should
scale PCS from a black point equal to ZERO in v2 profiles to the reference media black of
perceptual v4 PCS. Since I found many v2 profiles to be using a perceptual intent with black
point not zero at all, I'm implementing that as a black point compensation from whatever
black from perceptal intent to the reference media black for v4 profiles.

*/




int cdecl cmsChooseCnvrt(int Absolute,
                 int Phase1, LPcmsCIEXYZ BlackPointIn,
                             LPcmsCIEXYZ WhitePointIn,
                             LPcmsCIEXYZ IlluminantIn,
                             LPMAT3 ChromaticAdaptationMatrixIn,

                 int Phase2, LPcmsCIEXYZ BlackPointOut,
                             LPcmsCIEXYZ WhitePointOut,
                             LPcmsCIEXYZ IlluminantOut,
                             LPMAT3 ChromaticAdaptationMatrixOut,

                int DoBlackPointCompensation,
                double AdaptationState,
                 _cmsADJFN *fn1,
                 LPWMAT3 wm, LPWVEC3 wof);


// -------------------------------------------------------------------------

// D50 - Widely used

LCMSAPI LPcmsCIEXYZ LCMSEXPORT cmsD50_XYZ(void)
{
    static cmsCIEXYZ D50XYZ = {D50X, D50Y, D50Z};

    return &D50XYZ;
}

LCMSAPI LPcmsCIExyY LCMSEXPORT cmsD50_xyY(void)
{
    static cmsCIExyY D50xyY;
    cmsXYZ2xyY(&D50xyY, cmsD50_XYZ());

    return &D50xyY;
}


// ---------------- From LUT to LUT --------------------------


// Calculate m, offset Relativ -> Absolute undoing any chromatic
// adaptation done by the profile.

#ifdef _MSC_VER
#pragma warning(disable : 4100 4505)
#endif



// join scalings to obtain:
//     relative input to absolute and then to relative output

static
void Rel2RelStepAbsCoefs(double AdaptationState,

                         LPcmsCIEXYZ BlackPointIn,
                         LPcmsCIEXYZ WhitePointIn,
                         LPcmsCIEXYZ IlluminantIn,
                         LPMAT3 ChromaticAdaptationMatrixIn,

                         LPcmsCIEXYZ BlackPointOut,
                         LPcmsCIEXYZ WhitePointOut,
                         LPcmsCIEXYZ IlluminantOut,
                         LPMAT3 ChromaticAdaptationMatrixOut,

                         LPMAT3 m, LPVEC3 of)
{

       VEC3 WtPtIn, WtPtInAdapted;
       VEC3 WtPtOut, WtPtOutAdapted;
       MAT3 Scale, m1, m2, m3;

       VEC3init(&WtPtIn, WhitePointIn->X, WhitePointIn->Y, WhitePointIn->Z);
       MAT3eval(&WtPtInAdapted, ChromaticAdaptationMatrixIn, &WtPtIn);

       VEC3init(&WtPtOut, WhitePointOut->X, WhitePointOut->Y, WhitePointOut->Z);
       MAT3eval(&WtPtOutAdapted, ChromaticAdaptationMatrixOut, &WtPtOut);

       VEC3init(&Scale.v[0], WtPtInAdapted.n[0] / WtPtOutAdapted.n[0], 0, 0);
       VEC3init(&Scale.v[1], 0, WtPtInAdapted.n[1] / WtPtOutAdapted.n[1], 0);
       VEC3init(&Scale.v[2], 0, 0, WtPtInAdapted.n[2] / WtPtOutAdapted.n[2]);


       // Adaptation state

       if (AdaptationState == 1.0) {

           // Observer is fully adapted. Keep chromatic adaptation

           CopyMemory(m, &Scale, sizeof(MAT3));

       }
       else {

            // Observer is not adapted, undo the chromatic adaptation
            m1 = *ChromaticAdaptationMatrixIn;
            MAT3inverse(&m1, &m2);

            MAT3per(&m3, &m2, &Scale);
            MAT3per(m, &m3, ChromaticAdaptationMatrixOut);
       }


       VEC3init(of, 0.0, 0.0, 0.0);

}


// The (in)famous black point compensation. Right now implemented as
// a linear scaling in XYZ

static
void ComputeBlackPointCompensationFactors(LPcmsCIEXYZ BlackPointIn,
                      LPcmsCIEXYZ WhitePointIn,
                      LPcmsCIEXYZ IlluminantIn,
                      LPcmsCIEXYZ BlackPointOut,
                      LPcmsCIEXYZ WhitePointOut,
                      LPcmsCIEXYZ IlluminantOut,
                      LPMAT3 m, LPVEC3 of)
{


   cmsCIEXYZ RelativeBlackPointIn, RelativeBlackPointOut;
   double ax, ay, az, bx, by, bz, tx, ty, tz;

   // At first, convert both black points to relative.

   cmsAdaptToIlluminant(&RelativeBlackPointIn,  WhitePointIn, IlluminantIn, BlackPointIn);
   cmsAdaptToIlluminant(&RelativeBlackPointOut, WhitePointOut, IlluminantOut, BlackPointOut);

   // Now we need to compute a matrix plus an offset m and of such of
   // [m]*bpin + off = bpout
   // [m]*D50  + off = D50
   //
   // This is a linear scaling in the form ax+b, where
   // a = (bpout - D50) / (bpin - D50)
   // b = - D50* (bpout - bpin) / (bpin - D50)


   tx = RelativeBlackPointIn.X - IlluminantIn ->X;
   ty = RelativeBlackPointIn.Y - IlluminantIn ->Y;
   tz = RelativeBlackPointIn.Z - IlluminantIn ->Z;

   ax = (RelativeBlackPointOut.X - IlluminantOut ->X) / tx;
   ay = (RelativeBlackPointOut.Y - IlluminantOut ->Y) / ty;
   az = (RelativeBlackPointOut.Z - IlluminantOut ->Z) / tz;

   bx = - IlluminantOut -> X * (RelativeBlackPointOut.X - RelativeBlackPointIn.X) / tx;
   by = - IlluminantOut -> Y * (RelativeBlackPointOut.Y - RelativeBlackPointIn.Y) / ty;
   bz = - IlluminantOut -> Z * (RelativeBlackPointOut.Z - RelativeBlackPointIn.Z) / tz;


   MAT3identity(m);

   m->v[VX].n[0] = ax;
   m->v[VY].n[1] = ay;
   m->v[VZ].n[2] = az;

   VEC3init(of, bx, by, bz);

}

// Return TRUE if both m and of are empy -- "m" being identity and "of" being 0

static
LCMSBOOL IdentityParameters(LPWMAT3 m, LPWVEC3 of)
{
    WVEC3 wv0;

    VEC3initF(&wv0, 0, 0, 0);

    if (!MAT3isIdentity(m, 0.00001)) return FALSE;
    if (!VEC3equal(of, &wv0, 0.00001)) return FALSE;

    return TRUE;
}




// ----------------------------------------- Inter PCS conversions

// XYZ to XYZ linear scaling. Aso used on Black point compensation

static
void XYZ2XYZ(WORD In[], WORD Out[], LPWMAT3 m, LPWVEC3 of)
{

    WVEC3 a, r;

    a.n[0] = In[0] << 1;
    a.n[1] = In[1] << 1;
    a.n[2] = In[2] << 1;

    MAT3evalW(&r, m, &a);

    Out[0] = _cmsClampWord((r.n[VX] + of->n[VX]) >> 1);
    Out[1] = _cmsClampWord((r.n[VY] + of->n[VY]) >> 1);
    Out[2] = _cmsClampWord((r.n[VZ] + of->n[VZ]) >> 1);
}


// XYZ to Lab, scaling first

static
void XYZ2Lab(WORD In[], WORD Out[], LPWMAT3 m, LPWVEC3 of)
{
  WORD XYZ[3];

  XYZ2XYZ(In, XYZ, m, of);
  cmsXYZ2LabEncoded(XYZ, Out);
}

// Lab to XYZ, then scalling

static
void Lab2XYZ(WORD In[], WORD Out[], LPWMAT3 m, LPWVEC3 of)
{
       WORD XYZ[3];

       cmsLab2XYZEncoded(In, XYZ);
       XYZ2XYZ(XYZ, Out, m, of);
}

// Lab to XYZ, scalling and then, back to Lab

static
void Lab2XYZ2Lab(WORD In[], WORD Out[], LPWMAT3 m, LPWVEC3 of)
{
       WORD XYZ[3], XYZ2[3];

       cmsLab2XYZEncoded(In, XYZ);
       XYZ2XYZ(XYZ, XYZ2, m, of);
       cmsXYZ2LabEncoded(XYZ2, Out);
}

// ------------------------------------------------------------------

// Dispatcher for XYZ Relative LUT

static
int FromXYZRelLUT(int Absolute,
                             LPcmsCIEXYZ BlackPointIn,
                             LPcmsCIEXYZ WhitePointIn,
                             LPcmsCIEXYZ IlluminantIn,
                             LPMAT3 ChromaticAdaptationMatrixIn,

                 int Phase2, LPcmsCIEXYZ BlackPointOut,
                             LPcmsCIEXYZ WhitePointOut,
                             LPcmsCIEXYZ IlluminantOut,
                             LPMAT3 ChromaticAdaptationMatrixOut,

                 int DoBlackPointCompensation,
                 double AdaptationState,
                 _cmsADJFN *fn1,
                 LPMAT3 m, LPVEC3 of)

{
              switch (Phase2) {

                     // From relative XYZ to Relative XYZ.

                     case XYZRel:

                            if (Absolute)
                            {
                                   // From input relative to absolute, and then
                                   // back to output relative

                                   Rel2RelStepAbsCoefs(AdaptationState,
                                                       BlackPointIn,
                                                       WhitePointIn,
                                                       IlluminantIn,
                                                       ChromaticAdaptationMatrixIn,
                                                       BlackPointOut,
                                                       WhitePointOut,
                                                       IlluminantOut,
                                                       ChromaticAdaptationMatrixOut,
                                                       m, of);
                                   *fn1 = XYZ2XYZ;

                            }
                            else
                            {
                                   // XYZ Relative to XYZ relative, no op required
                                   *fn1 = NULL;
                                   if (DoBlackPointCompensation) {

                                      *fn1 = XYZ2XYZ;
                                      ComputeBlackPointCompensationFactors(BlackPointIn,
                                                                      WhitePointIn,
                                                                      IlluminantIn,
                                                                      BlackPointOut,
                                                                      WhitePointOut,
                                                                      IlluminantOut,
                                                                      m, of);

                                   }
                            }
                            break;


                     // From relative XYZ to Relative Lab

                     case LabRel:

                            // First pass XYZ to absolute, then to relative and
                            // finally to Lab. I use here D50 for output in order
                            // to prepare the "to Lab" conversion.

                            if (Absolute)
                            {

                                Rel2RelStepAbsCoefs(AdaptationState,
                                                    BlackPointIn,
                                                    WhitePointIn,
                                                    IlluminantIn,
                                                    ChromaticAdaptationMatrixIn,
                                                    BlackPointOut,
                                                    WhitePointOut,
                                                    IlluminantOut,
                                                    ChromaticAdaptationMatrixOut,
                                                    m, of);

                                *fn1 = XYZ2Lab;

                            }
                            else
                            {
                                   // Just Convert to Lab

                                   MAT3identity(m);
                                   VEC3init(of, 0, 0, 0);
                                   *fn1 = XYZ2Lab;

                                   if (DoBlackPointCompensation) {

                                    ComputeBlackPointCompensationFactors(BlackPointIn,
                                                                          WhitePointIn,
                                                                          IlluminantIn,
                                                                          BlackPointOut,
                                                                          WhitePointOut,
                                                                          IlluminantOut,
                                                                          m, of);
                                   }
                            }
                            break;


                     default: return FALSE;
                     }

              return TRUE;
}




// From Lab Relative type LUT

static
int FromLabRelLUT(int Absolute,
                             LPcmsCIEXYZ BlackPointIn,
                             LPcmsCIEXYZ WhitePointIn,
                             LPcmsCIEXYZ IlluminantIn,
                             LPMAT3 ChromaticAdaptationMatrixIn,

                 int Phase2, LPcmsCIEXYZ BlackPointOut,
                             LPcmsCIEXYZ WhitePointOut,
                             LPcmsCIEXYZ IlluminantOut,
                             LPMAT3 ChromaticAdaptationMatrixOut,

                int DoBlackPointCompensation,
                double AdaptationState,

                 _cmsADJFN *fn1,
                 LPMAT3 m, LPVEC3 of)
{

          switch (Phase2) {

              // From Lab Relative to XYZ Relative, very usual case

              case XYZRel:

                  if (Absolute) {  // Absolute intent

                            // From lab relative, to XYZ absolute, and then,
                            // back to XYZ relative

                            Rel2RelStepAbsCoefs(AdaptationState,
                                                BlackPointIn,
                                                WhitePointIn,
                                                cmsD50_XYZ(),
                                                ChromaticAdaptationMatrixIn,
                                                BlackPointOut,
                                                WhitePointOut,
                                                IlluminantOut,
                                                ChromaticAdaptationMatrixOut,
                                                m, of);

                            *fn1 = Lab2XYZ;

                     }
                     else
                     {
                            // From Lab relative, to XYZ relative.

                            *fn1 = Lab2XYZ;
                            if (DoBlackPointCompensation) {

                                 ComputeBlackPointCompensationFactors(BlackPointIn,
                                                                      WhitePointIn,
                                                                      IlluminantIn,
                                                                      BlackPointOut,
                                                                      WhitePointOut,
                                                                      IlluminantOut,
                                                                      m, of);

                            }
                     }
                     break;



              case LabRel:

                     if (Absolute) {

                             // First pass to XYZ using the input illuminant
                             // * InIlluminant / D50, then to absolute. Then
                             // to relative, but for input

                             Rel2RelStepAbsCoefs(AdaptationState,
                                                 BlackPointIn,
                                                 WhitePointIn, IlluminantIn,
                                                 ChromaticAdaptationMatrixIn,
                                                 BlackPointOut,
                                                 WhitePointOut, cmsD50_XYZ(),
                                                 ChromaticAdaptationMatrixOut,
                                                 m, of);
                             *fn1 = Lab2XYZ2Lab;
                     }
                     else
                     {      // Lab -> Lab relative don't need any adjust unless
                            // black point compensation

                            *fn1 = NULL;
                             if (DoBlackPointCompensation) {

                                 *fn1 = Lab2XYZ2Lab;
                                 ComputeBlackPointCompensationFactors(BlackPointIn,
                                                                      WhitePointIn,
                                                                      IlluminantIn,
                                                                      BlackPointOut,
                                                                      WhitePointOut,
                                                                      IlluminantOut,
                                                                      m, of);


                            }
                     }
                     break;


              default: return FALSE;
              }

   return TRUE;
}


// This function does calculate the necessary conversion operations
// needed from transpassing data from a LUT to a LUT. The conversion
// is modeled as a pointer of function and two coefficients, a and b
// The function is actually called only if not null pointer is provided,
// and the two paramaters are passed in. There are several types of
// conversions, but basically they do a linear scalling and a interchange



// Main dispatcher

int cmsChooseCnvrt(int Absolute,
                  int Phase1, LPcmsCIEXYZ BlackPointIn,
                              LPcmsCIEXYZ WhitePointIn,
                              LPcmsCIEXYZ IlluminantIn,
                              LPMAT3 ChromaticAdaptationMatrixIn,

                  int Phase2, LPcmsCIEXYZ BlackPointOut,
                              LPcmsCIEXYZ WhitePointOut,
                              LPcmsCIEXYZ IlluminantOut,
                              LPMAT3 ChromaticAdaptationMatrixOut,

                  int DoBlackPointCompensation,
                  double AdaptationState,
                  _cmsADJFN *fn1,
                  LPWMAT3 wm, LPWVEC3 wof)
{

       int rc;
       MAT3 m;
       VEC3 of;


       MAT3identity(&m);
       VEC3init(&of, 0, 0, 0);

       switch (Phase1) {

       // Input LUT is giving XYZ relative values.

       case XYZRel:  rc = FromXYZRelLUT(Absolute,
                                          BlackPointIn,
                                          WhitePointIn,
                                          IlluminantIn,
                                          ChromaticAdaptationMatrixIn,
                                          Phase2,
                                          BlackPointOut,
                                          WhitePointOut,
                                          IlluminantOut,
                                          ChromaticAdaptationMatrixOut,
                                          DoBlackPointCompensation,
                                          AdaptationState,
                                          fn1, &m, &of);
                     break;



       // Input LUT is giving Lab relative values

       case LabRel:  rc =  FromLabRelLUT(Absolute,
                                          BlackPointIn,
                                          WhitePointIn,
                                          IlluminantIn,
                                          ChromaticAdaptationMatrixIn,
                                          Phase2,
                                          BlackPointOut,
                                          WhitePointOut,
                                          IlluminantOut,
                                          ChromaticAdaptationMatrixOut,
                                          DoBlackPointCompensation,
                                          AdaptationState,
                                          fn1, &m, &of);
                     break;




       // Unrecognized combination

       default:    cmsSignalError(LCMS_ERRC_ABORTED, "(internal) Phase error");
                   return FALSE;

       }

       MAT3toFix(wm, &m);
       VEC3toFix(wof, &of);

       // Do some optimization -- discard conversion if identity parameters.

       if (*fn1 == XYZ2XYZ || *fn1 == Lab2XYZ2Lab) {

           if (IdentityParameters(wm, wof))
               *fn1 = NULL;
       }


       return rc;
}



