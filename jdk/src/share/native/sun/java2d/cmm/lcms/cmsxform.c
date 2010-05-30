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


// Transformations stuff
// -----------------------------------------------------------------------


// Interface

cmsHTRANSFORM LCMSEXPORT cmsCreateTransform(cmsHPROFILE Input,
                                       DWORD InputFormat,
                                       cmsHPROFILE Output,
                                       DWORD OutputFormat,
                                       int Intent,
                                       DWORD dwFlags);

cmsHTRANSFORM LCMSEXPORT cmsCreateProofingTransform(cmsHPROFILE Input,
                                               DWORD InputFormat,
                                               cmsHPROFILE Output,
                                               DWORD OutputFormat,
                                               cmsHPROFILE Proofing,
                                               int Intent,
                                               int ProofingIntent,
                                               DWORD dwFlags);


void         LCMSEXPORT cmsDeleteTransform(cmsHTRANSFORM hTransform);

void         LCMSEXPORT cmsDoTransform(cmsHTRANSFORM Transform,
                                  LPVOID InputBuffer,
                                  LPVOID OutputBuffer, unsigned int Size);

void         LCMSEXPORT cmsGetAlarmCodes(int *r, int *g, int *b);
void         LCMSEXPORT cmsSetAlarmCodes(int r, int g, int b);
LCMSBOOL     LCMSEXPORT cmsIsIntentSupported(cmsHPROFILE hProfile,
                                                int Intent, int UsedDirection);

// -------------------------------------------------------------------------


// Alarm RGB codes

static WORD AlarmR = 0x8fff, AlarmG = 0x8fff, AlarmB = 0x8fff;

// Tag tables, soted by intents

static icTagSignature Device2PCS[] = {icSigAToB0Tag,       // Perceptual
                                      icSigAToB1Tag,       // Relative colorimetric
                                      icSigAToB2Tag,       // Saturation
                                      icSigAToB1Tag };     // Absolute colorimetric
                                                           // (Relative/WhitePoint)

static icTagSignature PCS2Device[] = {icSigBToA0Tag,       // Perceptual
                                      icSigBToA1Tag,       // Relative colorimetric
                                      icSigBToA2Tag,       // Saturation
                                      icSigBToA1Tag };     // Absolute colorimetric
                                                           // (Relative/WhitePoint)


static icTagSignature Preview[]    = {icSigPreview0Tag,
                                      icSigPreview1Tag,
                                      icSigPreview2Tag,
                                      icSigPreview1Tag };



static volatile double GlobalAdaptationState = 0;

// --------------------------------Stages--------------------------------------

// Following routines does implement several kind of steps inside
// transform. On building the transform, code chooses adequate.


// From Shaper-Matrix to PCS

static
void ShaperMatrixToPCS(struct _cmstransform_struct *p,
                     WORD In[3], WORD Out[3])
{
       cmsEvalMatShaper(p -> InMatShaper, In, Out);
}

// From LUT to PCS

static
void LUTtoPCS(struct _cmstransform_struct *p,
                     WORD In[], WORD Out[3])
{
       cmsEvalLUT(p -> Device2PCS, In, Out);
}

// From indexed named color to PCS

static
void NC2toPCS(struct _cmstransform_struct *p,
                     WORD In[], WORD Out[3])
{
    int index = In[0];

    if (index >= p ->NamedColorList-> nColors)
        cmsSignalError(LCMS_ERRC_WARNING, "Color %d out of range", index);
    else
        CopyMemory(Out, p ->NamedColorList->List[index].PCS, 3 * sizeof(WORD));
}

// From PCS to Shaper-Matrix

static
void PCStoShaperMatrix(struct _cmstransform_struct *p,
                     WORD In[3], WORD Out[3])
{
       cmsEvalMatShaper(p -> OutMatShaper, In, Out);
}

// From PCS to LUT

static
void PCStoLUT(struct _cmstransform_struct *p,
                     WORD In[3], WORD Out[])
{
       cmsEvalLUT(p -> PCS2Device, In, Out);
}




// ----------------------- TRANSFORMATIONS --------------------------


// Inlining some assignations

#define COPY_3CHANS(to, from) { to[0]=from[0]; to[1]=from[1]; to[2]=from[2]; }


// Null transformation, only hold channels

static
void NullXFORM(_LPcmsTRANSFORM p,
                     LPVOID in,
                     LPVOID out, unsigned int Size)
{
       register LPBYTE accum;
       register LPBYTE output;
       WORD wIn[MAXCHANNELS];
       register unsigned int i, n;


       accum  = (LPBYTE) in;
       output = (LPBYTE) out;
       n = Size;                    // Buffer len

       for (i=0; i < n; i++)
       {
       accum = p -> FromInput(p, wIn, accum);
       output = p -> ToOutput(p, wIn, output);
       }

}


// This is the "normal" proofing transform

static
void NormalXFORM(_LPcmsTRANSFORM p,
                     LPVOID in,
                     LPVOID out, unsigned int Size)
{
       register LPBYTE accum;
       register LPBYTE output;
       WORD wIn[MAXCHANNELS], wOut[MAXCHANNELS];
       WORD wStageABC[3], wPCS[3], wStageLMN[MAXCHANNELS];
       WORD wGamut[1];
       register unsigned int i, n;



       accum  = (LPBYTE) in;
       output = (LPBYTE) out;
       n = Size;                    // Buffer len

       for (i=0; i < n; i++)
       {

       accum = p -> FromInput(p, wIn, accum);

       p -> FromDevice(p, wIn, wStageABC);

       if (p -> Stage1) {

              p -> Stage1(wStageABC, wPCS, &p->m1, &p->of1);

              if (wPCS[0] == 0xFFFF &&
                  wPCS[1] == 0xFFFF &&
                  wPCS[2] == 0xFFFF) {

                     // White cutoff

                     output = p -> ToOutput((_LPcmsTRANSFORM) p,
                                   _cmsWhiteBySpace(cmsGetColorSpace(p -> OutputProfile)),
                                   output);
                     continue;
                     }
              }
       else
              COPY_3CHANS(wPCS, wStageABC);


       if (p->Gamut) {

       // Gamut check, enabled across CLUT

       cmsEvalLUT(p -> Gamut, wPCS, wGamut);

       if (wGamut[0] >= 1) {

              wOut[0] = AlarmR;          // Gamut alarm
              wOut[1] = AlarmG;
              wOut[2] = AlarmB;
              wOut[3] = 0;

              output = p -> ToOutput((_LPcmsTRANSFORM)p, wOut, output);
              continue;
              }
       }

       if (p -> Preview)
       {
              WORD wPreview[3];    // PCS

              cmsEvalLUT(p -> Preview, wPCS, wPreview);
              COPY_3CHANS(wPCS, wPreview);
       }

       if (p -> Stage2) {

              p -> Stage2(wPCS, wStageLMN, &p->m2, &p->of2);

              if (wPCS[0] == 0xFFFF &&
                  wPCS[1] == 0xFFFF &&
                  wPCS[2] == 0xFFFF) {

                     // White cutoff

                     output = p -> ToOutput((_LPcmsTRANSFORM)p,
                                   _cmsWhiteBySpace(cmsGetColorSpace(p -> OutputProfile)),
                                   output);

                     continue;
                     }

              }
       else
              COPY_3CHANS(wStageLMN, wPCS);

       // Here wOut may come as MAXCHANNELS channels

       p -> ToDevice(p, wStageLMN, wOut);

       output = p -> ToOutput((_LPcmsTRANSFORM)p, wOut, output);
       }
}

// Using precalculated LUT

static
void PrecalculatedXFORM(_LPcmsTRANSFORM p,
                     LPVOID in,
                     LPVOID out, unsigned int Size)
{
       register LPBYTE accum;
       register LPBYTE output;
       WORD wIn[MAXCHANNELS], wOut[MAXCHANNELS];
       unsigned int i, n;


       accum  = (LPBYTE) in;
       output = (LPBYTE) out;
       n = Size;                    // Buffer len


       for (i=0; i < n; i++) {

         accum = p -> FromInput(p, wIn, accum);

         // Try to speedup things on plain devicelinks

         if (p ->DeviceLink ->wFlags == LUT_HAS3DGRID) {

           p ->DeviceLink ->CLut16params.Interp3D(wIn, wOut,
                                    p ->DeviceLink -> T,
                                    &p ->DeviceLink -> CLut16params);
         }
         else
            cmsEvalLUT(p -> DeviceLink, wIn, wOut);


          output = p -> ToOutput(p, wOut, output);
       }
}

// Auxiliar: Handle precalculated gamut check

static
void TransformOnePixelWithGamutCheck(_LPcmsTRANSFORM p, WORD wIn[], WORD wOut[])
{
    WORD wOutOfGamut;

       cmsEvalLUT(p ->GamutCheck,  wIn, &wOutOfGamut);

       if (wOutOfGamut >= 1) {

              ZeroMemory(wOut, sizeof(WORD) * MAXCHANNELS);

              wOut[0] = AlarmR;
              wOut[1] = AlarmG;
              wOut[2] = AlarmB;

       }
       else
            cmsEvalLUT(p -> DeviceLink, wIn, wOut);

}


static
void PrecalculatedXFORMGamutCheck(_LPcmsTRANSFORM p,
                                  LPVOID in,
                                  LPVOID out, unsigned int Size)
{
       register LPBYTE accum;
       register LPBYTE output;
       WORD wIn[MAXCHANNELS], wOut[MAXCHANNELS];
       register unsigned int i, n;


       accum  = (LPBYTE) in;
       output = (LPBYTE) out;
       n = Size;                    // Buffer len

       for (i=0; i < n; i++) {

       accum = p -> FromInput(p, wIn, accum);

       TransformOnePixelWithGamutCheck(p, wIn, wOut);

       output = p -> ToOutput(p, wOut, output);
       }
}



// Using precalculated LUT + Cache

static
void CachedXFORM(_LPcmsTRANSFORM p,
                     LPVOID in,
                     LPVOID out, unsigned int Size)
{
       register LPBYTE accum;
       register LPBYTE output;
       WORD wIn[MAXCHANNELS], wOut[MAXCHANNELS];
       register unsigned int i, n;
       WORD CacheIn[MAXCHANNELS], CacheOut[MAXCHANNELS];


       accum  = (LPBYTE) in;
       output = (LPBYTE) out;
       n = Size;                    // Buffer len

       // Empty buffers for quick memcmp

       ZeroMemory(wIn,  sizeof(WORD) * MAXCHANNELS);
       ZeroMemory(wOut, sizeof(WORD) * MAXCHANNELS);


       LCMS_READ_LOCK(&p ->rwlock);
           CopyMemory(CacheIn,  p ->CacheIn, sizeof(WORD) * MAXCHANNELS);
           CopyMemory(CacheOut, p ->CacheOut, sizeof(WORD) * MAXCHANNELS);
       LCMS_UNLOCK(&p ->rwlock);

       for (i=0; i < n; i++) {

       accum = p -> FromInput(p, wIn, accum);


       if (memcmp(wIn, CacheIn, sizeof(WORD) * MAXCHANNELS) == 0) {

            CopyMemory(wOut, CacheOut, sizeof(WORD) * MAXCHANNELS);
       }
       else {

            // Try to speedup things on plain devicelinks

             if (p ->DeviceLink ->wFlags == LUT_HAS3DGRID) {

             p ->DeviceLink ->CLut16params.Interp3D(wIn, wOut,
                                    p ->DeviceLink -> T,
                                    &p ->DeviceLink -> CLut16params);
             }
             else
                  cmsEvalLUT(p -> DeviceLink, wIn, wOut);


            CopyMemory(CacheIn,  wIn,  sizeof(WORD) * MAXCHANNELS);
            CopyMemory(CacheOut, wOut, sizeof(WORD) * MAXCHANNELS);
       }

       output = p -> ToOutput(p, wOut, output);
       }


       LCMS_WRITE_LOCK(&p ->rwlock);
           CopyMemory(p->CacheIn,  CacheIn, sizeof(WORD) * MAXCHANNELS);
           CopyMemory(p->CacheOut, CacheOut, sizeof(WORD) * MAXCHANNELS);
       LCMS_UNLOCK(&p ->rwlock);

}



// Using precalculated LUT + Cache

static
void CachedXFORMGamutCheck(_LPcmsTRANSFORM p,
                           LPVOID in,
                           LPVOID out, unsigned int Size)
{
       register LPBYTE accum;
       register LPBYTE output;
       WORD wIn[MAXCHANNELS], wOut[MAXCHANNELS];
       register unsigned int i, n;
       WORD CacheIn[MAXCHANNELS], CacheOut[MAXCHANNELS];


       accum  = (LPBYTE) in;
       output = (LPBYTE) out;
       n = Size;                    // Buffer len

       // Empty buffers for quick memcmp

       ZeroMemory(wIn,  sizeof(WORD) * MAXCHANNELS);
       ZeroMemory(wOut, sizeof(WORD) * MAXCHANNELS);

       LCMS_READ_LOCK(&p ->rwlock);
           CopyMemory(CacheIn,  p ->CacheIn, sizeof(WORD) * MAXCHANNELS);
           CopyMemory(CacheOut, p ->CacheOut, sizeof(WORD) * MAXCHANNELS);
       LCMS_UNLOCK(&p ->rwlock);


       for (i=0; i < n; i++) {

       accum = p -> FromInput(p, wIn, accum);

       if (memcmp(wIn, CacheIn, sizeof(WORD) * MAXCHANNELS) == 0) {

            CopyMemory(wOut, CacheOut, sizeof(WORD) * MAXCHANNELS);
       }
       else {

            TransformOnePixelWithGamutCheck(p, wIn, wOut);

            CopyMemory(CacheIn, wIn, sizeof(WORD) * MAXCHANNELS);
            CopyMemory(CacheOut, wOut, sizeof(WORD) * MAXCHANNELS);
       }

       output = p -> ToOutput(p, wOut, output);
       }

        LCMS_WRITE_LOCK(&p ->rwlock);
           CopyMemory(p->CacheIn,  CacheIn, sizeof(WORD) * MAXCHANNELS);
           CopyMemory(p->CacheOut, CacheOut, sizeof(WORD) * MAXCHANNELS);
        LCMS_UNLOCK(&p ->rwlock);
}


// Using smelted Matrix/Shaper

static
void MatrixShaperXFORM(_LPcmsTRANSFORM p,
                     LPVOID in,
                     LPVOID out, unsigned int Size)
{
       register LPBYTE accum;
       register LPBYTE output;
       WORD wIn[MAXCHANNELS], wOut[MAXCHANNELS];
       register unsigned int i, n;


       accum  = (LPBYTE) in;
       output = (LPBYTE) out;
       n = Size;                    // Buffer len

       for (i=0; i < n; i++)
       {
       accum = p -> FromInput(p, wIn, accum);
       cmsEvalMatShaper(p -> SmeltMatShaper, wIn, wOut);
       output = p -> ToOutput(p, wOut, output);
       }
}


// Using Named color input table

static
void NC2deviceXform(_LPcmsTRANSFORM p,
                     LPVOID in,
                     LPVOID out, unsigned int Size)
{

       register LPBYTE accum;
       register LPBYTE output;
       WORD wIn[MAXCHANNELS], wOut[MAXCHANNELS];
       register unsigned int i;


       accum  = (LPBYTE) in;
       output = (LPBYTE) out;

       for (i=0; i < Size; i++) {

       accum = p -> FromInput(p, wIn, accum);
       CopyMemory(wOut, p ->NamedColorList->List[wIn[0]].DeviceColorant, sizeof(WORD) * MAXCHANNELS);
       output = p -> ToOutput(p, wOut, output);
       }

}



// --------------------------------------------------------------------------
// Build a LUT based on shape-matrix method.


// Some non-conformant gray profiles are using kTCR as L*,
// this function converts the curve to XYZ PCS.

static
void FromLstarToXYZ(LPGAMMATABLE g, LPGAMMATABLE gxyz[3])
{
    int i;
    int nPoints = 4096;
    cmsCIELab Lab;
    cmsCIEXYZ XYZ;
    L16PARAMS L16;

    // Setup interpolation across origin
    cmsCalcL16Params(g ->nEntries, &L16);

    // Allocate curves
    gxyz[0] = cmsAllocGamma(nPoints);
    gxyz[1] = cmsAllocGamma(nPoints);
    gxyz[2] = cmsAllocGamma(nPoints);

    // Transport from Lab to XYZ

    for (i=0; i < nPoints; i++) {

        WORD val = _cmsQuantizeVal(i, nPoints);
        WORD w   = cmsLinearInterpLUT16(val, g->GammaTable, &L16);

        Lab.L = ((double) 100.0 * w ) / 65535.0;
        Lab.a = Lab.b = 0;

        cmsLab2XYZ(NULL, &XYZ, &Lab);

        // Should be same curve
        gxyz[0] ->GammaTable[i] = (WORD) floor((65535.0 * XYZ.X) / D50X + 0.5);
        gxyz[1] ->GammaTable[i] = (WORD) floor((65535.0 * XYZ.Y) / D50Y + 0.5);
        gxyz[2] ->GammaTable[i] = (WORD) floor((65535.0 * XYZ.Z) / D50Z + 0.5);
    }
}

// Monochrome version

static
LPMATSHAPER cmsBuildGrayInputMatrixShaper(cmsHPROFILE hProfile)
{
       cmsCIEXYZ Illuminant;
       LPGAMMATABLE GrayTRC, Shapes[3];
       LPMATSHAPER MatShaper;
       MAT3 Scale;

       GrayTRC = cmsReadICCGamma(hProfile, icSigGrayTRCTag);        // Y
       if (GrayTRC == NULL) return NULL;

       cmsTakeIluminant(&Illuminant, hProfile);

       if (cmsGetPCS(hProfile) == icSigLabData) {

                // Fixup for Lab monochrome
                FromLstarToXYZ(GrayTRC, Shapes);
       }
       else  {
                Shapes[0] = cmsDupGamma(GrayTRC);
                Shapes[1] = cmsDupGamma(GrayTRC);
                Shapes[2] = cmsDupGamma(GrayTRC);
       }

       if (!Shapes[0] || !Shapes[1] || !Shapes[2])
              return NULL;

       cmsFreeGamma(GrayTRC);

       // R=G=B as precondition

       VEC3init(&Scale.v[0], Illuminant.X/3,  Illuminant.X/3,  Illuminant.X/3);
       VEC3init(&Scale.v[1], Illuminant.Y/3,  Illuminant.Y/3,  Illuminant.Y/3);
       VEC3init(&Scale.v[2], Illuminant.Z/3,  Illuminant.Z/3,  Illuminant.Z/3);


       MatShaper = cmsAllocMatShaper(&Scale, Shapes, MATSHAPER_INPUT);
       cmsFreeGammaTriple(Shapes);
       return MatShaper;

}


// Monochrome as output

static
LPMATSHAPER cmsBuildGrayOutputMatrixShaper(cmsHPROFILE hProfile)
{
       cmsCIEXYZ Illuminant;
       LPGAMMATABLE GrayTRC, Shapes[3];
       LPMATSHAPER MatShaper;
       MAT3 Scale;

       cmsTakeIluminant(&Illuminant, hProfile);

       // That is a special case for non-compliant profiles.

       if (cmsGetPCS(hProfile) == icSigLabData) {

                LPGAMMATABLE Shapes1[3];

                GrayTRC = cmsReadICCGamma(hProfile, icSigGrayTRCTag);
                FromLstarToXYZ(GrayTRC, Shapes1);

                // Reversing must be done after curve translation

                Shapes[0] = cmsReverseGamma(Shapes1[0]->nEntries, Shapes1[0]);
                Shapes[1] = cmsReverseGamma(Shapes1[1]->nEntries, Shapes1[1]);
                Shapes[2] = cmsReverseGamma(Shapes1[2]->nEntries, Shapes1[2]);

                cmsFreeGammaTriple(Shapes1);

       }
       else  {

                // Normal case

                GrayTRC = cmsReadICCGammaReversed(hProfile, icSigGrayTRCTag);   // Y

                Shapes[0] = cmsDupGamma(GrayTRC);
                Shapes[1] = cmsDupGamma(GrayTRC);
                Shapes[2] = cmsDupGamma(GrayTRC);
       }

       if (!Shapes[0] || !Shapes[1] || !Shapes[2])
              return NULL;

       cmsFreeGamma(GrayTRC);

       VEC3init(&Scale.v[0], 0,  1.0/Illuminant.Y,  0);
       VEC3init(&Scale.v[1], 0,  1.0/Illuminant.Y,  0);
       VEC3init(&Scale.v[2], 0,  1.0/Illuminant.Y,  0);


       MatShaper = cmsAllocMatShaper(&Scale, Shapes, MATSHAPER_OUTPUT);
       cmsFreeGammaTriple(Shapes);
       return MatShaper;

}



// Input matrix, only in XYZ

LPMATSHAPER cmsBuildInputMatrixShaper(cmsHPROFILE InputProfile)
{
       MAT3 DoubleMat;
       LPGAMMATABLE Shapes[3];
       LPMATSHAPER InMatSh;

       // Check if this is a grayscale profile. If so, build
       // appropiate conversion tables. The tables are the PCS
       // iluminant, scaled across GrayTRC

       if (cmsGetColorSpace(InputProfile) == icSigGrayData)
       {
              return cmsBuildGrayInputMatrixShaper(InputProfile);
       }

       if (!cmsReadICCMatrixRGB2XYZ(&DoubleMat, InputProfile))
                     return NULL;

       Shapes[0] = cmsReadICCGamma(InputProfile, icSigRedTRCTag);
       Shapes[1] = cmsReadICCGamma(InputProfile, icSigGreenTRCTag);
       Shapes[2] = cmsReadICCGamma(InputProfile, icSigBlueTRCTag);

       if (!Shapes[0] || !Shapes[1] || !Shapes[2])
                     return NULL;

       InMatSh = cmsAllocMatShaper(&DoubleMat, Shapes, MATSHAPER_INPUT);

       cmsFreeGammaTriple(Shapes);

       return InMatSh;
}


// Output style matrix-shaper


LPMATSHAPER cmsBuildOutputMatrixShaper(cmsHPROFILE OutputProfile)
{
       MAT3 DoubleMat, DoubleInv;
       LPGAMMATABLE InverseShapes[3];
       LPMATSHAPER OutMatSh;



       if (cmsGetColorSpace(OutputProfile) == icSigGrayData)
       {
              return cmsBuildGrayOutputMatrixShaper(OutputProfile);
       }


       if (!cmsReadICCMatrixRGB2XYZ(&DoubleMat, OutputProfile))
                     return NULL;

       if (MAT3inverse(&DoubleMat, &DoubleInv) < 0)
              return NULL;


       InverseShapes[0] = cmsReadICCGammaReversed(OutputProfile, icSigRedTRCTag);
       InverseShapes[1] = cmsReadICCGammaReversed(OutputProfile, icSigGreenTRCTag);
       InverseShapes[2] = cmsReadICCGammaReversed(OutputProfile, icSigBlueTRCTag);

       if (InverseShapes[0] == NULL ||
           InverseShapes[1] == NULL ||
           InverseShapes[2] == NULL) return NULL;

       OutMatSh = cmsAllocMatShaper(&DoubleInv, InverseShapes, MATSHAPER_OUTPUT);

       cmsFreeGammaTriple(InverseShapes);

       return OutMatSh;
}



// This function builds a transform matrix chaining parameters

static
LCMSBOOL cmsBuildSmeltMatShaper(_LPcmsTRANSFORM p)
{
       MAT3 From, To, ToInv, Transfer;
       LPGAMMATABLE In[3], InverseOut[3];


       if (!cmsReadICCMatrixRGB2XYZ(&From, p -> InputProfile))
                     return FALSE;


       if (!cmsReadICCMatrixRGB2XYZ(&To, p -> OutputProfile))
                     return FALSE;

       // invert dest

       if (MAT3inverse(&To, &ToInv) < 0)
                        return FALSE;

       // Multiply
        MAT3per(&Transfer, &ToInv, &From);


        // Read gamma curves

        In[0] = cmsReadICCGamma(p -> InputProfile, icSigRedTRCTag);
        In[1] = cmsReadICCGamma(p -> InputProfile, icSigGreenTRCTag);
        In[2] = cmsReadICCGamma(p -> InputProfile, icSigBlueTRCTag);

        if (!In[0] || !In[1] || !In[2])
                     return FALSE;


        InverseOut[0] = cmsReadICCGammaReversed(p -> OutputProfile, icSigRedTRCTag);
        InverseOut[1] = cmsReadICCGammaReversed(p -> OutputProfile, icSigGreenTRCTag);
        InverseOut[2] = cmsReadICCGammaReversed(p -> OutputProfile, icSigBlueTRCTag);

        if (!InverseOut[0] || !InverseOut[1] || !InverseOut[2]) {
                     cmsFreeGammaTriple(In);
                     return FALSE;
        }

        p -> SmeltMatShaper = cmsAllocMatShaper2(&Transfer, In, InverseOut, MATSHAPER_ALLSMELTED);

        cmsFreeGammaTriple(In);
        cmsFreeGammaTriple(InverseOut);

        return (p -> SmeltMatShaper != NULL);
}




// Conversion between PCS ------------------------------------------

// Identifies intent archieved by LUT

static
int GetPhase(cmsHPROFILE hProfile)
{
       switch (cmsGetPCS(hProfile)) {

       case icSigXYZData: return XYZRel;

       case icSigLabData: return LabRel;

       default:  cmsSignalError(LCMS_ERRC_ABORTED, "Invalid PCS");
       }

       return XYZRel;
}




static
void TakeConversionRoutines(_LPcmsTRANSFORM p, int DoBPC)
{
       cmsCIEXYZ BlackPointIn, WhitePointIn, IlluminantIn;
       cmsCIEXYZ BlackPointOut, WhitePointOut, IlluminantOut;
       cmsCIEXYZ BlackPointProof, WhitePointProof, IlluminantProof;
       MAT3 ChromaticAdaptationMatrixIn, ChromaticAdaptationMatrixOut;
       MAT3 ChromaticAdaptationMatrixProof;


       cmsTakeIluminant(&IlluminantIn,        p -> InputProfile);
       cmsTakeMediaWhitePoint(&WhitePointIn,  p -> InputProfile);
       cmsTakeMediaBlackPoint(&BlackPointIn,  p -> InputProfile);
       cmsReadChromaticAdaptationMatrix(&ChromaticAdaptationMatrixIn, p -> InputProfile);

       cmsTakeIluminant(&IlluminantOut,        p -> OutputProfile);
       cmsTakeMediaWhitePoint(&WhitePointOut,  p -> OutputProfile);
       cmsTakeMediaBlackPoint(&BlackPointOut,  p -> OutputProfile);
       cmsReadChromaticAdaptationMatrix(&ChromaticAdaptationMatrixOut, p -> OutputProfile);


       if (p -> Preview == NULL && p ->Gamut == NULL)     // Non-proofing
       {
            if (p ->Intent == INTENT_PERCEPTUAL ||
                p ->Intent == INTENT_SATURATION) {


                    // For v4 profiles, Perceptual PCS has a reference black point
                    // which v2 profiles should scale to.

                    if ((cmsGetProfileICCversion(p ->InputProfile) >= 0x4000000) ||
                        (cmsGetProfileICCversion(p ->OutputProfile) >= 0x4000000)) {

                           DoBPC = TRUE;
                    }
            }

            // Black point compensation does not apply to absolute intent

            if (p ->Intent == INTENT_ABSOLUTE_COLORIMETRIC)
                            DoBPC = FALSE;

            // Black point compensation does not apply to devicelink profiles

            if (cmsGetDeviceClass(p ->InputProfile) == icSigLinkClass)
                            DoBPC = FALSE;

            if (cmsGetDeviceClass(p ->OutputProfile) == icSigLinkClass)
                            DoBPC = FALSE;

            if (DoBPC) {

                // Detect Black points

                cmsDetectBlackPoint(&BlackPointIn,    p->InputProfile,   p->Intent, 0);
                cmsDetectBlackPoint(&BlackPointOut,   p->OutputProfile,  p->Intent, 0);

                // If equal black points, then do nothing. This often applies to BP=0

                if (BlackPointIn.X == BlackPointOut.X &&
                    BlackPointIn.Y == BlackPointOut.Y &&
                    BlackPointIn.Z == BlackPointOut.Z)
                                DoBPC = FALSE;


            }

            cmsChooseCnvrt(p -> Intent == INTENT_ABSOLUTE_COLORIMETRIC,

                 p -> Phase1,
                             &BlackPointIn,
                             &WhitePointIn,
                             &IlluminantIn,
                             &ChromaticAdaptationMatrixIn,

                 p -> Phase3,
                             &BlackPointOut,
                             &WhitePointOut,
                             &IlluminantOut,
                             &ChromaticAdaptationMatrixOut,

                 DoBPC,
                 p ->AdaptationState,
                 &p->Stage1,
                 &p->m1, &p->of1);

       }
       else // Proofing
       {


       cmsTakeIluminant(&IlluminantProof,        p -> PreviewProfile);
       cmsTakeMediaWhitePoint(&WhitePointProof,  p -> PreviewProfile);
       cmsTakeMediaBlackPoint(&BlackPointProof,  p -> PreviewProfile);
       cmsReadChromaticAdaptationMatrix(&ChromaticAdaptationMatrixProof, p -> PreviewProfile);

       if (DoBPC) {

            cmsDetectBlackPoint(&BlackPointProof, p->PreviewProfile, p->Intent, 0);
            cmsDetectBlackPoint(&BlackPointIn,    p->InputProfile,   p->Intent, 0);
            cmsDetectBlackPoint(&BlackPointOut,   p->OutputProfile,  p->Intent, 0);

            // Reality check

            if (BlackPointIn.X == BlackPointProof.X &&
                BlackPointIn.Y == BlackPointProof.Y &&
                BlackPointIn.Z == BlackPointProof.Z)
                                DoBPC = FALSE;


       }



       cmsChooseCnvrt(p -> Intent == INTENT_ABSOLUTE_COLORIMETRIC,

                 p -> Phase1,
                             &BlackPointIn,
                             &WhitePointIn,
                             &IlluminantIn,
                             &ChromaticAdaptationMatrixIn,

                 p -> Phase2,
                             &BlackPointProof,
                             &WhitePointProof,
                             &IlluminantProof,
                             &ChromaticAdaptationMatrixProof,
                 DoBPC,
                 p ->AdaptationState,
                 &p->Stage1,
                 &p->m1, &p->of1);

       cmsChooseCnvrt(p -> ProofIntent == INTENT_ABSOLUTE_COLORIMETRIC,

                 p -> Phase2,
                             &BlackPointProof,
                             &WhitePointProof,
                             &IlluminantProof,
                             &ChromaticAdaptationMatrixProof,

                 p -> Phase3,
                             &BlackPointOut,
                             &WhitePointOut,
                             &IlluminantOut,
                             &ChromaticAdaptationMatrixOut,
                 0,
                 0.0,
                 &p->Stage2,
                 &p->m2, &p->of2);
       }

}


// Check colorspace

static
LCMSBOOL IsProperColorSpace(cmsHPROFILE hProfile, DWORD dwFormat, LCMSBOOL lUsePCS)
{
       int Space = T_COLORSPACE(dwFormat);

       if (Space == PT_ANY) return TRUE;

       if (lUsePCS)
           return (Space == _cmsLCMScolorSpace(cmsGetPCS(hProfile)));
       else
           return (Space == _cmsLCMScolorSpace(cmsGetColorSpace(hProfile)));
}


// Auxiliary: allocate transform struct and set to defaults

static
_LPcmsTRANSFORM AllocEmptyTransform(void)
{
    // Allocate needed memory

    _LPcmsTRANSFORM p = (_LPcmsTRANSFORM) _cmsMalloc(sizeof(_cmsTRANSFORM));
    if (!p) {

          cmsSignalError(LCMS_ERRC_ABORTED, "cmsCreateTransform: _cmsMalloc() failed");
          return NULL;
    }

    ZeroMemory(p, sizeof(_cmsTRANSFORM));

    // Initialize default methods

    p -> xform          = NULL;
    p -> Intent         = INTENT_PERCEPTUAL;
    p -> ProofIntent    = INTENT_ABSOLUTE_COLORIMETRIC;
    p -> DoGamutCheck   = FALSE;
    p -> InputProfile   = NULL;
    p -> OutputProfile  = NULL;
    p -> PreviewProfile = NULL;
    p -> Preview        = NULL;
    p -> Gamut          = NULL;
    p -> DeviceLink     = NULL;
    p -> InMatShaper    = NULL;
    p -> OutMatShaper   = NULL;
    p -> SmeltMatShaper = NULL;
    p -> NamedColorList = NULL;
    p -> EntryColorSpace = (icColorSpaceSignature) 0;
    p -> ExitColorSpace  = (icColorSpaceSignature) 0;
    p -> AdaptationState = GlobalAdaptationState;

    LCMS_CREATE_LOCK(&p->rwlock);

    return p;
}


// Identify whatever a transform is to be cached

static
void SetPrecalculatedTransform(_LPcmsTRANSFORM p)
{
    if ((p->dwOriginalFlags & cmsFLAGS_GAMUTCHECK) && p ->GamutCheck != NULL) {

        p -> xform = PrecalculatedXFORMGamutCheck;

        if (!(p->dwOriginalFlags & cmsFLAGS_NOTCACHE)) {

            ZeroMemory(p ->CacheIn, sizeof(WORD) * MAXCHANNELS);
            TransformOnePixelWithGamutCheck(p, p->CacheIn, p ->CacheOut);
            p ->xform = CachedXFORMGamutCheck;
        }

    }
    else {

        p -> xform = PrecalculatedXFORM;

        if (!(p->dwOriginalFlags & cmsFLAGS_NOTCACHE)) {

            ZeroMemory(p ->CacheIn, sizeof(WORD) * MAXCHANNELS);
            cmsEvalLUT(p ->DeviceLink, p->CacheIn, p ->CacheOut);
            p ->xform = CachedXFORM;
        }
    }
}


// Transform is identified as device-link
static
cmsHPROFILE CreateDeviceLinkTransform(_LPcmsTRANSFORM p)
{

    if (!IsProperColorSpace(p->InputProfile, p->InputFormat, FALSE)) {
        cmsSignalError(LCMS_ERRC_ABORTED, "Device link is operating on wrong colorspace on input");
        return NULL;
    }

    if (!IsProperColorSpace(p->InputProfile, p->OutputFormat, TRUE)) {
        cmsSignalError(LCMS_ERRC_ABORTED, "Device link is operating on wrong colorspace on output");
        return NULL;
    }

    // Device link does only have AToB0Tag (ICC-Spec 1998/09)

    p->DeviceLink = cmsReadICCLut(p->InputProfile, icSigAToB0Tag);

    if (!p->DeviceLink) {

         cmsSignalError(LCMS_ERRC_ABORTED, "Noncompliant device-link profile");
         cmsDeleteTransform((cmsHTRANSFORM) p);
         return NULL;
         }

    if (p ->PreviewProfile != NULL) {
            cmsSignalError(LCMS_ERRC_WARNING, "Proofing not supported on device link transforms");
    }

    if (p ->OutputProfile != NULL) {
            cmsSignalError(LCMS_ERRC_WARNING, "Output profile should be NULL, since this is a device-link transform");
    }

    p -> Phase1 = -1;
    p -> Phase2 = -1;
    p -> Phase3 = -1;

    SetPrecalculatedTransform(p);

    p -> EntryColorSpace = cmsGetColorSpace(p -> InputProfile);
    p -> ExitColorSpace  = cmsGetPCS(p -> InputProfile);

    if (p ->EntryColorSpace == icSigRgbData ||
        p ->EntryColorSpace == icSigCmyData) {

                    p->DeviceLink -> CLut16params.Interp3D = cmsTetrahedralInterp16;
    }

    // Precalculated device-link profile is ready
    return (cmsHTRANSFORM) p;
}


// Transform that includes proofing
static
void CreateProof(_LPcmsTRANSFORM p, icTagSignature *ToTagPtr)

{
    icTagSignature ProofTag;

    if (p -> dwOriginalFlags & cmsFLAGS_SOFTPROOFING) {

      // Apr-15, 2002 - Too much profiles does have bogus content
      // on preview tag, so I do compute it by my own.

      p -> Preview = _cmsComputeSoftProofLUT(p ->PreviewProfile, p ->Intent);
      p -> Phase2  = LabRel;

      // That's a proofing transfor, so use second intent for output.

      *ToTagPtr  = PCS2Device[p->ProofIntent];

      if (p -> Preview == NULL) {

        ProofTag = Preview[p -> Intent];

        if (!cmsIsTag(p ->PreviewProfile,  ProofTag)) {

            ProofTag = Preview[0];
            if (!cmsIsTag(p ->PreviewProfile,  ProofTag))
                            ProofTag = (icTagSignature)0;
        }

        if (ProofTag) {

             p -> Preview = cmsReadICCLut(p ->PreviewProfile, ProofTag);
             p -> Phase2 = GetPhase(p ->PreviewProfile);

        }
        else
             {
             p -> Preview = NULL;
             p ->PreviewProfile = NULL;
             cmsSignalError(LCMS_ERRC_WARNING, "Sorry, the proof profile has not previewing capabilities");
             }
      }

    }


    // Aug-31, 2001 - Too much profiles does have bogus content
    // on gamut tag, so I do compute it by my own.

    if ((p -> dwOriginalFlags & cmsFLAGS_GAMUTCHECK) && (p -> dwOriginalFlags & cmsFLAGS_NOTPRECALC)) {


             p -> Gamut = _cmsComputeGamutLUT(p->PreviewProfile, p ->Intent);
             p -> Phase2  = LabRel;

             if (p -> Gamut == NULL) {

                // Profile goes only in one direction... try to see
                // if profile has the tag, and use it, no matter it
                // could be bogus. This is the last chance!

                if (cmsIsTag(p ->PreviewProfile, icSigGamutTag)) {

                    p -> Gamut = cmsReadICCLut(p ->PreviewProfile, icSigGamutTag);

                    }
                    else   {

                     // Nope, cannot be done.

                     cmsSignalError(LCMS_ERRC_WARNING, "Sorry, the proof profile has not gamut checking capabilities");
                     p -> Gamut = NULL;
                    }
             }

      }

}

// Choose the adequate transform routine

static
_LPcmsTRANSFORM PickTransformRoutine(_LPcmsTRANSFORM p,
                                    icTagSignature *FromTagPtr,
                                    icTagSignature *ToTagPtr)
{




        // Is a named color profile?
        if (cmsGetDeviceClass(p->InputProfile) == icSigNamedColorClass) {

                  // Yes, and used as input
                  p ->FromDevice = NC2toPCS;
        }
        else {
                // Can we optimize matrix-shaper only transform?

                   if ((*FromTagPtr == 0) &&
                       (*ToTagPtr == 0) &&
                       (!p->PreviewProfile) &&
                       (p -> Intent != INTENT_ABSOLUTE_COLORIMETRIC) &&
                       (p -> EntryColorSpace == icSigRgbData) &&
                       (p -> ExitColorSpace == icSigRgbData)  &&
                       !(p -> dwOriginalFlags & cmsFLAGS_BLACKPOINTCOMPENSATION)) {

                          // Yes... try to smelt matrix-shapers
                          p -> xform = MatrixShaperXFORM;
                          p -> dwOriginalFlags |= cmsFLAGS_NOTPRECALC;

                          if (!cmsBuildSmeltMatShaper(p))
                          {
                                 cmsSignalError(LCMS_ERRC_ABORTED, "unable to smelt shaper-matrix, required tags missing");
                                 return NULL;
                          }

                          p -> Phase1 = p -> Phase3 = XYZRel;
                          return p;

                }

                // No, is a transform involving LUT

                if (*FromTagPtr != 0) {

                     p -> FromDevice = LUTtoPCS;
                     p -> Device2PCS = cmsReadICCLut(p -> InputProfile, *FromTagPtr);
                     if (!p -> Device2PCS) {

                            cmsSignalError(LCMS_ERRC_ABORTED, "profile is unsuitable for input");
                            return NULL;
                            }

              }
              else
              {
                     p -> FromDevice = ShaperMatrixToPCS;
                     p -> InMatShaper = cmsBuildInputMatrixShaper(p -> InputProfile);

                     if (!p ->InMatShaper) {
                            cmsSignalError(LCMS_ERRC_ABORTED, "profile is unsuitable for input");
                            return NULL;
                            }

                     p -> Phase1 = XYZRel;

              }
              }

              if (*ToTagPtr != 0) {

                     p -> ToDevice = PCStoLUT;
                     p -> PCS2Device = cmsReadICCLut(p -> OutputProfile, *ToTagPtr);
                     if (!p -> PCS2Device) {
                            cmsSignalError(LCMS_ERRC_ABORTED, "profile is unsuitable for output");
                            return NULL;
                            }

                     }
              else
              {
                     p -> ToDevice = PCStoShaperMatrix;
                     p -> OutMatShaper = cmsBuildOutputMatrixShaper(p->OutputProfile);

                     if (!p -> OutMatShaper) {
                            cmsSignalError(LCMS_ERRC_ABORTED, "profile is unsuitable for output");
                            return NULL;
                            }
                     p -> Phase3 = XYZRel;

              }


       return p;
}




// Create a transform.

cmsHTRANSFORM LCMSEXPORT cmsCreateProofingTransform(cmsHPROFILE InputProfile,
                                               DWORD InputFormat,
                                               cmsHPROFILE OutputProfile,
                                               DWORD OutputFormat,
                                               cmsHPROFILE ProofingProfile,
                                               int nIntent,
                                               int ProofingIntent,
                                               DWORD dwFlags)

{
       _LPcmsTRANSFORM p;
       icTagSignature FromTag;
       icTagSignature ToTag;

       if (nIntent < 0 || nIntent > 3 ||
           ProofingIntent < 0 || ProofingIntent > 3) {

            cmsSignalError(LCMS_ERRC_ABORTED, "cmsCreateTransform: intent mismatch");
            return NULL;
       }

       p = AllocEmptyTransform();
       if (p == NULL) return NULL;

       p -> xform           = NormalXFORM;
       p -> Intent          = nIntent;
       p -> ProofIntent     = ProofingIntent;
       p -> DoGamutCheck    = FALSE;
       p -> InputProfile    = InputProfile;
       p -> OutputProfile   = OutputProfile;
       p -> PreviewProfile  = ProofingProfile;
       p -> InputFormat     = InputFormat;
       p -> OutputFormat    = OutputFormat;
       p -> dwOriginalFlags = dwFlags;

       p -> lInputV4Lab     = p ->lOutputV4Lab = FALSE;


       p -> FromInput = _cmsIdentifyInputFormat(p, InputFormat);
       p -> ToOutput  = _cmsIdentifyOutputFormat(p, OutputFormat);

       // Null transform can be done without profiles
       if ((p->dwOriginalFlags & cmsFLAGS_NULLTRANSFORM) ||
                        ((InputProfile == NULL) &&
                         (OutputProfile == NULL))) {

            p -> xform = NullXFORM;
            return (cmsHTRANSFORM) p;
       }

       // From here we need at least one input profile
       if (InputProfile == NULL) {

          cmsSignalError(LCMS_ERRC_ABORTED, "Input profile cannot be NULL!");
          cmsDeleteTransform((cmsHTRANSFORM) p);
          return NULL;
       }


       //  Device link are means to store precalculated transform grids.
       if (cmsGetDeviceClass(InputProfile) == icSigLinkClass) {

            return CreateDeviceLinkTransform(p);
       }

       if (!IsProperColorSpace(InputProfile, InputFormat, FALSE)) {

              cmsSignalError(LCMS_ERRC_ABORTED, "Input profile is operating on wrong colorspace");
              cmsDeleteTransform((cmsHTRANSFORM) p);
              return NULL;
       }

       p ->EntryColorSpace = cmsGetColorSpace(InputProfile);

       // Oct-21-2002: Added named color transforms
       if (cmsGetDeviceClass(InputProfile) == icSigNamedColorClass) {

        if (p ->NamedColorList == NULL)
                p ->NamedColorList = cmsAllocNamedColorList(0);

        cmsReadICCnamedColorList(p, InputProfile, icSigNamedColor2Tag);

        // Special case. If output profile == NULL, then the transform gives
        // device values from named colors.

        if (OutputProfile == NULL) {

            p ->ExitColorSpace = p -> EntryColorSpace;
            p ->xform = NC2deviceXform;
            return (cmsHTRANSFORM) p;
        }

        // Named color doesn't precalc anything
        p -> dwOriginalFlags |= cmsFLAGS_NOTPRECALC;
       }


       // From here we need also output profile.
      if (OutputProfile == NULL) {
          cmsSignalError(LCMS_ERRC_ABORTED, "Output profile cannot be NULL!");
          cmsDeleteTransform((cmsHTRANSFORM) p);
          return NULL;
       }


       if (!IsProperColorSpace(OutputProfile, OutputFormat, FALSE)) {
              cmsSignalError(LCMS_ERRC_ABORTED, "Output profile is operating on wrong colorspace");
              cmsDeleteTransform((cmsHTRANSFORM) p);
              return NULL;
       }

       p -> ExitColorSpace = cmsGetColorSpace(OutputProfile);

       // Named color only on input
       if (cmsGetDeviceClass(OutputProfile) == icSigNamedColorClass) {

           cmsSignalError(LCMS_ERRC_ABORTED, "Named color profiles are not supported as output");
           cmsDeleteTransform((cmsHTRANSFORM) p);
           return NULL;
       }

       p -> Phase1 = GetPhase(InputProfile);
       p -> Phase2 = -1;
       p -> Phase3 = GetPhase(OutputProfile);

       // Try to locate a LUT

       FromTag  = Device2PCS[nIntent];
       ToTag    = PCS2Device[nIntent];

       if (!cmsIsTag(InputProfile, FromTag)) {

              FromTag = Device2PCS[0];

              if (!cmsIsTag(InputProfile,  FromTag)) {
                            FromTag = (icTagSignature)0;
              }
       }

       // If proofing is needed, add required tags/parameters
       if (ProofingProfile)
           CreateProof(p, &ToTag);


       if (!cmsIsTag(OutputProfile,  ToTag)) {

           ToTag = PCS2Device[0];

           // 12-Dec-2003, Abstract profiles can be placed as output and still using AToB0
           if (cmsGetDeviceClass(OutputProfile) == icSigAbstractClass) {

                    if (!cmsIsTag(OutputProfile,  ToTag)) {
                                ToTag = (icTagSignature) icSigAToB0Tag;
                    }
           }

           if (!cmsIsTag(OutputProfile,  ToTag))
                            ToTag = (icTagSignature)0;
       }


       if (p-> dwOriginalFlags & cmsFLAGS_MATRIXINPUT)
              FromTag = (icTagSignature)0;

       if (p -> dwOriginalFlags & cmsFLAGS_MATRIXOUTPUT)
              ToTag = (icTagSignature)0;



       if (PickTransformRoutine(p, &FromTag, &ToTag) == NULL) {

          cmsDeleteTransform((cmsHTRANSFORM) p);
          return NULL;

       }

       TakeConversionRoutines(p, dwFlags & cmsFLAGS_BLACKPOINTCOMPENSATION);

       if (!(p -> dwOriginalFlags & cmsFLAGS_NOTPRECALC)) {

               LPLUT DeviceLink;
               LPLUT GamutCheck = NULL;


               if (p ->EntryColorSpace == icSigCmykData &&
                   p ->ExitColorSpace == icSigCmykData &&
                   (dwFlags & cmsFLAGS_PRESERVEBLACK)) {

                    DeviceLink = _cmsPrecalculateBlackPreservingDeviceLink((cmsHTRANSFORM) p, dwFlags);

                    // Cannot be done at all?
                    if (DeviceLink == NULL)
                            DeviceLink = _cmsPrecalculateDeviceLink((cmsHTRANSFORM) p, dwFlags);

               }
               else {

                    DeviceLink = _cmsPrecalculateDeviceLink((cmsHTRANSFORM) p, dwFlags);
               }

               // Allow to specify cmsFLAGS_GAMUTCHECK, even if no proofing profile is given
               if ((p ->PreviewProfile != NULL) && (p -> dwOriginalFlags & cmsFLAGS_GAMUTCHECK)) {

                   GamutCheck = _cmsPrecalculateGamutCheck((cmsHTRANSFORM) p);
               }

               // If input colorspace is Rgb, Cmy, then use tetrahedral interpolation
               // for speed reasons (it only works well on spaces on Luma is diagonal, and
               // not if luma is in separate channel)
               if (p ->EntryColorSpace == icSigRgbData ||
                   p ->EntryColorSpace == icSigCmyData) {


                    cmsCalcCLUT16ParamsEx(DeviceLink->CLut16params.nSamples,
                                          DeviceLink->CLut16params.nInputs,
                                          DeviceLink->CLut16params.nOutputs,
                                          TRUE, &DeviceLink->CLut16params);

               }

               // If this is a 8-bit transform, optimize LUT further.

                if ((T_BYTES(InputFormat) == 1) && (T_CHANNELS(InputFormat) == 3)) {

                    DeviceLink = _cmsBlessLUT8(DeviceLink);
                    if (DeviceLink == NULL) return NULL;

                }


                p ->GamutCheck = GamutCheck;

                if (DeviceLink) {

                    p ->DeviceLink = DeviceLink;

                    if ((nIntent != INTENT_ABSOLUTE_COLORIMETRIC) &&
                        !(p -> dwOriginalFlags & cmsFLAGS_NOWHITEONWHITEFIXUP))

                            _cmsFixWhiteMisalignment(p);

                }
                else
                {

                     cmsSignalError(LCMS_ERRC_ABORTED,
                                "Cannot precalculate %d->%d channels transform!",
                                T_CHANNELS(InputFormat), T_CHANNELS(OutputFormat));

                     cmsDeleteTransform(p);
                     return NULL;
                }


              SetPrecalculatedTransform(p);


       }

       // Re-Identify formats
       p -> FromInput = _cmsIdentifyInputFormat(p, InputFormat);
       p -> ToOutput  = _cmsIdentifyOutputFormat(p, OutputFormat);


       return p;
}


// Wrapper por simpler non-proofing transforms.

cmsHTRANSFORM LCMSEXPORT cmsCreateTransform(cmsHPROFILE Input,
                                       DWORD InputFormat,
                                       cmsHPROFILE Output,
                                       DWORD OutputFormat,
                                       int Intent,
                                       DWORD dwFlags)

{
       return cmsCreateProofingTransform(Input, InputFormat,
                                         Output, OutputFormat,
                                         NULL,
                                         Intent, INTENT_ABSOLUTE_COLORIMETRIC,
                                         dwFlags);
}


// Profiles are *NOT* closed

void LCMSEXPORT cmsDeleteTransform(cmsHTRANSFORM hTransform)
{
       _LPcmsTRANSFORM p = (_LPcmsTRANSFORM) (LPSTR) hTransform;

       if (p -> Device2PCS)
              cmsFreeLUT(p -> Device2PCS);
       if (p -> PCS2Device)
              cmsFreeLUT(p -> PCS2Device);
       if (p -> Gamut)
              cmsFreeLUT(p -> Gamut);
       if (p -> Preview)
              cmsFreeLUT(p -> Preview);
       if (p -> DeviceLink)
              cmsFreeLUT(p -> DeviceLink);
       if (p -> InMatShaper)
              cmsFreeMatShaper(p -> InMatShaper);
       if (p -> OutMatShaper)
              cmsFreeMatShaper(p -> OutMatShaper);
       if (p -> SmeltMatShaper)
              cmsFreeMatShaper(p -> SmeltMatShaper);
       if (p ->NamedColorList)
              cmsFreeNamedColorList(p ->NamedColorList);
       if (p -> GamutCheck)
            cmsFreeLUT(p -> GamutCheck);

       LCMS_FREE_LOCK(&p->rwlock);

       _cmsFree((void *) p);
}


// Apply transform code
void LCMSEXPORT cmsDoTransform(cmsHTRANSFORM Transform,
                    LPVOID InputBuffer,
                    LPVOID OutputBuffer, unsigned int Size)

{

            _LPcmsTRANSFORM p = (_LPcmsTRANSFORM) (LPSTR) Transform;

            p -> StrideIn = p -> StrideOut = Size;

            p -> xform(p, InputBuffer, OutputBuffer, Size);

}


void LCMSEXPORT cmsSetAlarmCodes(int r, int g, int b)
{
       AlarmR = RGB_8_TO_16(r);
       AlarmG = RGB_8_TO_16(g);
       AlarmB = RGB_8_TO_16(b);
}

void LCMSEXPORT cmsGetAlarmCodes(int *r, int *g, int *b)
{
       *r = RGB_16_TO_8(AlarmR);
       *g = RGB_16_TO_8(AlarmG);
       *b = RGB_16_TO_8(AlarmB);
}

// Returns TRUE if the profile is implemented as matrix-shaper

LCMSBOOL LCMSEXPORT _cmsIsMatrixShaper(cmsHPROFILE hProfile)
{
    switch (cmsGetColorSpace(hProfile)) {

    case icSigGrayData:

        return cmsIsTag(hProfile, icSigGrayTRCTag);

    case icSigRgbData:

        return (cmsIsTag(hProfile, icSigRedColorantTag) &&
                cmsIsTag(hProfile, icSigGreenColorantTag) &&
                cmsIsTag(hProfile, icSigBlueColorantTag) &&
                cmsIsTag(hProfile, icSigRedTRCTag) &&
                cmsIsTag(hProfile, icSigGreenTRCTag) &&
                cmsIsTag(hProfile, icSigBlueTRCTag));

    default:

        return FALSE;
    }
}


LCMSBOOL LCMSEXPORT cmsIsIntentSupported(cmsHPROFILE hProfile,
                                                int Intent, int UsedDirection)
{

     icTagSignature* TagTable;

     // Device link profiles only implements the intent in header

     if (cmsGetDeviceClass(hProfile) != icSigLinkClass) {

       switch (UsedDirection) {

       case LCMS_USED_AS_INPUT: TagTable = Device2PCS; break;
       case LCMS_USED_AS_OUTPUT:TagTable = PCS2Device; break;
       case LCMS_USED_AS_PROOF: TagTable = Preview; break;

       default:
        cmsSignalError(LCMS_ERRC_ABORTED, "Unexpected direction (%d)", UsedDirection);
        return FALSE;
       }

       if (cmsIsTag(hProfile, TagTable[Intent])) return TRUE;
       return _cmsIsMatrixShaper(hProfile);
     }

     return (cmsTakeRenderingIntent(hProfile) == Intent);
}

// Multiple profile transform.
static
int MultiprofileSampler(register WORD In[], register WORD Out[], register LPVOID Cargo)
{
    cmsHTRANSFORM* Transforms = (cmsHTRANSFORM*) Cargo;
    int i;

    cmsDoTransform(Transforms[0], In, Out, 1);

    for (i=1; Transforms[i]; i++)
        cmsDoTransform(Transforms[i], Out, Out, 1);



    return TRUE;
}


static
int IsAllowedInSingleXform(icProfileClassSignature aClass)
{
    return (aClass == icSigInputClass) ||
           (aClass == icSigDisplayClass) ||
           (aClass == icSigOutputClass) ||
           (aClass == icSigColorSpaceClass);
}


// A multiprofile transform does chain several profiles into a single
// devicelink. It couls also be used to merge named color profiles into
// a single database.


cmsHTRANSFORM LCMSEXPORT cmsCreateMultiprofileTransform(cmsHPROFILE hProfiles[],
                                                                int nProfiles,
                                                                DWORD dwInput,
                                                                DWORD dwOutput,
                                                                int Intent,
                                                                DWORD dwFlags)
{
    cmsHTRANSFORM Transforms[257];
    DWORD dwPrecalcFlags = (dwFlags|cmsFLAGS_NOTPRECALC|cmsFLAGS_NOTCACHE);
    DWORD FormatInput, FormatOutput;
    cmsHPROFILE hLab, hXYZ, hProfile;
    icColorSpaceSignature ColorSpace, CurrentColorSpace;
    icColorSpaceSignature ColorSpaceIn, ColorSpaceOut;
    LPLUT Grid;
    int nGridPoints, ChannelsInput, ChannelsOutput = 3, i;
    _LPcmsTRANSFORM p;
    int nNamedColor;

    if (nProfiles > 255) {
        cmsSignalError(LCMS_ERRC_ABORTED, "What are you trying to do with more that 255 profiles?!?, of course aborted");
        return NULL;
    }

    // There is a simple case with just two profiles, try to catch it in order of getting
    // black preservation to work on this function, at least with two profiles.


    if (nProfiles == 2) {

        icProfileClassSignature Class1 = cmsGetDeviceClass(hProfiles[0]);
        icProfileClassSignature Class2 = cmsGetDeviceClass(hProfiles[1]);

        // Only input, output and display are allowed

        if (IsAllowedInSingleXform(Class1) &&
            IsAllowedInSingleXform(Class2))
                   return cmsCreateTransform(hProfiles[0], dwInput, hProfiles[1], dwOutput, Intent, dwFlags);
    }


    // Creates a phantom transform for latter filling
    p = (_LPcmsTRANSFORM) cmsCreateTransform(NULL, dwInput,
                                             NULL, dwOutput, Intent, cmsFLAGS_NULLTRANSFORM);

    // If user wants null one, give it
    if (dwFlags & cmsFLAGS_NULLTRANSFORM) return (cmsHPROFILE) p;

    // Is a bunch of named color profiles?
    nNamedColor = 0;
    for (i=0; i < nProfiles; i++) {
        if (cmsGetDeviceClass(hProfiles[i]) == icSigNamedColorClass)
                nNamedColor++;
    }


    if (nNamedColor == nProfiles) {

            // Yes, only named color. Create a named color-device
            // and append to named color table

            cmsDeleteTransform((cmsHTRANSFORM) p);

            p = (_LPcmsTRANSFORM) cmsCreateTransform(hProfiles[0], dwInput, NULL, dwOutput, Intent, dwFlags);
            for (i=1; i < nProfiles; i++) {
                    cmsReadICCnamedColorList(p, hProfiles[i], icSigNamedColor2Tag);
            }

            return p;   // Ok, done so far
    }
    else
    if (nNamedColor > 0) {

        cmsDeleteTransform((cmsHTRANSFORM) p);
        cmsSignalError(LCMS_ERRC_ABORTED, "Could not mix named color profiles with other types in multiprofile transform");
        return NULL;
    }


    // We will need a 3DCLUT for device link
    Grid =  cmsAllocLUT();
    if (!Grid) return NULL;

    // This one is our PCS (Always Lab)
    hLab  = cmsCreateLabProfile(NULL);
    hXYZ  = cmsCreateXYZProfile();

    if (!hLab || !hXYZ) goto ErrorCleanup;

    // Take some info....

    p ->EntryColorSpace = CurrentColorSpace = cmsGetColorSpace(hProfiles[0]);


    for (i=0; i < nProfiles; i++) {

        int lIsDeviceLink, lIsInput;

        // Check colorspace

        hProfile      = hProfiles[i];
        lIsDeviceLink = (cmsGetDeviceClass(hProfile) == icSigLinkClass);
        lIsInput      = (CurrentColorSpace != icSigXYZData) &&
                        (CurrentColorSpace != icSigLabData);

        if (lIsInput) {

            ColorSpaceIn    = cmsGetColorSpace(hProfile);
            ColorSpaceOut   = cmsGetPCS(hProfile);

        }
        else {

            ColorSpaceIn    = cmsGetPCS(hProfile);
            ColorSpaceOut   = cmsGetColorSpace(hProfile);
        }

        ChannelsInput  = _cmsChannelsOf(ColorSpaceIn);
        ChannelsOutput = _cmsChannelsOf(ColorSpaceOut);

        FormatInput    = BYTES_SH(2)|CHANNELS_SH(ChannelsInput);
        FormatOutput   = BYTES_SH(2)|CHANNELS_SH(ChannelsOutput);

        ColorSpace = ColorSpaceIn;


        if (ColorSpace == CurrentColorSpace) {

            if (lIsDeviceLink) {

                Transforms[i]  = cmsCreateTransform(hProfile, FormatInput,
                                                    NULL,     FormatOutput,
                                                    Intent,   dwPrecalcFlags);
            }

            else {

                if (lIsInput) {

                        Transforms[i]  = cmsCreateTransform(hProfile, FormatInput,
                                                  (ColorSpaceOut == icSigLabData ? hLab : hXYZ), FormatOutput,
                                                  Intent, dwPrecalcFlags);
                }
                else {
                        Transforms[i]  = cmsCreateTransform((ColorSpaceIn == icSigLabData ? hLab : hXYZ), FormatInput,
                                                  hProfile, FormatOutput,
                                                  Intent, dwPrecalcFlags);

                }
            }


        }
        else  // Can come from pcs?
        if (CurrentColorSpace == icSigXYZData) {

            Transforms[i] = cmsCreateTransform(hXYZ, FormatInput,
                                              hProfile, FormatOutput,
                                              Intent, dwPrecalcFlags);

        }
        else
        if (CurrentColorSpace == icSigLabData) {

            Transforms[i] = cmsCreateTransform(hLab, FormatInput,
                                              hProfile, FormatOutput,
                                              Intent, dwPrecalcFlags);

        }
        else {
                cmsSignalError(LCMS_ERRC_ABORTED, "cmsCreateMultiprofileTransform: ColorSpace mismatch");
                goto ErrorCleanup;
        }

        if (Transforms[i] == NULL) {
            cmsSignalError(LCMS_ERRC_ABORTED, "cmsCreateMultiprofileTransform: unable to create transform");
            goto ErrorCleanup;
        }
        CurrentColorSpace = ColorSpaceOut;

    }

    p ->ExitColorSpace = CurrentColorSpace;
    Transforms[i] = NULL;   // End marker

    p ->InputProfile  = hProfiles[0];
    p ->OutputProfile = hProfiles[nProfiles - 1];

    nGridPoints = _cmsReasonableGridpointsByColorspace(p ->EntryColorSpace, dwFlags);

    ChannelsInput  = _cmsChannelsOf(cmsGetColorSpace(p ->InputProfile));

    Grid = cmsAlloc3DGrid(Grid, nGridPoints, ChannelsInput, ChannelsOutput);

    if (!(dwFlags & cmsFLAGS_NOPRELINEARIZATION))
           _cmsComputePrelinearizationTablesFromXFORM(Transforms, nProfiles, Grid);

    // Compute device link on 16-bit basis
    if (!cmsSample3DGrid(Grid, MultiprofileSampler, (LPVOID) Transforms, Grid -> wFlags)) {

                cmsFreeLUT(Grid);
                goto ErrorCleanup;
    }

    // All ok, store the newly created LUT
    p -> DeviceLink   = Grid;

    SetPrecalculatedTransform(p);

    for (i=nProfiles-1; i >= 0; --i)
        cmsDeleteTransform(Transforms[i]);


    if (hLab) cmsCloseProfile(hLab);
    if (hXYZ) cmsCloseProfile(hXYZ);


    if (p ->EntryColorSpace == icSigRgbData ||
        p ->EntryColorSpace == icSigCmyData) {

                    p->DeviceLink -> CLut16params.Interp3D = cmsTetrahedralInterp16;
    }


    if ((Intent != INTENT_ABSOLUTE_COLORIMETRIC) &&
        !(dwFlags & cmsFLAGS_NOWHITEONWHITEFIXUP))
                            _cmsFixWhiteMisalignment(p);

    return (cmsHTRANSFORM) p;


ErrorCleanup:

    if (hLab) cmsCloseProfile(hLab);
    if (hXYZ) cmsCloseProfile(hXYZ);
    return NULL;
}



double LCMSEXPORT cmsSetAdaptationState(double d)
{
    double OldVal = GlobalAdaptationState;

    if (d >= 0)
            GlobalAdaptationState = d;

    return OldVal;

}
