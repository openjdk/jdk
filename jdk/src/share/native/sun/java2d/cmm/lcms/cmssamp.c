/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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


// ---------------------------------------------------------------------------------

static volatile int GlobalBlackPreservationStrategy = 0;

// Quantize a value 0 <= i < MaxSamples

WORD _cmsQuantizeVal(double i, int MaxSamples)
{
       double x;

       x = ((double) i * 65535.) / (double) (MaxSamples - 1);

       return (WORD) floor(x + .5);
}


// Is a table linear?

int cmsIsLinear(WORD Table[], int nEntries)
{
       register int i;
       int diff;

       for (i=0; i < nEntries; i++) {

           diff = abs((int) Table[i] - (int) _cmsQuantizeVal(i, nEntries));
           if (diff > 3)
                     return 0;
       }

       return 1;
}



// pow() restricted to integer

static
int ipow(int base, int exp)
{
        int res = base;

        while (--exp)
               res *= base;

        return res;
}


// Given n, 0<=n<=clut^dim, returns the colorant.

static
int ComponentOf(int n, int clut, int nColorant)
{
        if (nColorant <= 0)
                return (n % clut);

        n /= ipow(clut, nColorant);

        return (n % clut);
}



// This routine does a sweep on whole input space, and calls its callback
// function on knots. returns TRUE if all ok, FALSE otherwise.

LCMSBOOL LCMSEXPORT cmsSample3DGrid(LPLUT Lut, _cmsSAMPLER Sampler, LPVOID Cargo, DWORD dwFlags)
{
   int i, t, nTotalPoints, Colorant, index;
   WORD In[MAXCHANNELS], Out[MAXCHANNELS];

   nTotalPoints = ipow(Lut->cLutPoints, Lut -> InputChan);

   index = 0;
   for (i = 0; i < nTotalPoints; i++) {

        for (t=0; t < (int) Lut -> InputChan; t++) {

                Colorant =  ComponentOf(i, Lut -> cLutPoints, (Lut -> InputChan - t  - 1 ));
                In[t]    = _cmsQuantizeVal(Colorant, Lut -> cLutPoints);
        }


        if (dwFlags & SAMPLER_HASTL1) {

                 for (t=0; t < (int) Lut -> InputChan; t++)
                     In[t] = cmsReverseLinearInterpLUT16(In[t],
                                                Lut -> L1[t],
                                                &Lut -> In16params);
        }

        for (t=0; t < (int) Lut -> OutputChan; t++)
                     Out[t] = Lut->T[index + t];

        if (dwFlags & SAMPLER_HASTL2) {

             for (t=0; t < (int) Lut -> OutputChan; t++)
                     Out[t] = cmsLinearInterpLUT16(Out[t],
                                                   Lut -> L2[t],
                                                   &Lut -> Out16params);
        }


        if (!Sampler(In, Out, Cargo))
                return FALSE;

        if (!(dwFlags & SAMPLER_INSPECT)) {

            if (dwFlags & SAMPLER_HASTL2) {

                for (t=0; t < (int) Lut -> OutputChan; t++)
                     Out[t] = cmsReverseLinearInterpLUT16(Out[t],
                                                   Lut -> L2[t],
                                                   &Lut -> Out16params);
                }


            for (t=0; t < (int) Lut -> OutputChan; t++)
                        Lut->T[index + t] = Out[t];

        }

        index += Lut -> OutputChan;

    }

    return TRUE;
}






// choose reasonable resolution
int _cmsReasonableGridpointsByColorspace(icColorSpaceSignature Colorspace, DWORD dwFlags)
{
    int nChannels;

    // Already specified?
    if (dwFlags & 0x00FF0000) {
            // Yes, grab'em
            return (dwFlags >> 16) & 0xFF;
    }

    nChannels = _cmsChannelsOf(Colorspace);

    // HighResPrecalc is maximum resolution

    if (dwFlags & cmsFLAGS_HIGHRESPRECALC) {

        if (nChannels > 4)
                return 7;       // 7 for Hifi

        if (nChannels == 4)     // 23 for CMYK
                return 23;

        return 49;      // 49 for RGB and others
    }


    // LowResPrecal is stripped resolution

    if (dwFlags & cmsFLAGS_LOWRESPRECALC) {

        if (nChannels > 4)
                return 6;       // 6 for Hifi

        if (nChannels == 1)
                return 33;      // For monochrome

        return 17;              // 17 for remaining
    }

    // Default values

    if (nChannels > 4)
                return 7;       // 7 for Hifi

    if (nChannels == 4)
                return 17;      // 17 for CMYK

    return 33;                  // 33 for RGB

}

// Sampler implemented by another transform. This is a clean way to
// precalculate the devicelink 3D CLUT for almost any transform

static
int XFormSampler(register WORD In[], register WORD Out[], register LPVOID Cargo)
{
        cmsDoTransform((cmsHTRANSFORM) Cargo, In, Out, 1);
        return TRUE;
}

// This routine does compute the devicelink CLUT containing whole
// transform. Handles any channel number.

LPLUT _cmsPrecalculateDeviceLink(cmsHTRANSFORM h, DWORD dwFlags)
{
       _LPcmsTRANSFORM p = (_LPcmsTRANSFORM) h;
       LPLUT Grid;
       int nGridPoints;
       DWORD dwFormatIn, dwFormatOut;
       DWORD SaveFormatIn, SaveFormatOut;
       int ChannelsIn, ChannelsOut;
       LPLUT SaveGamutLUT;


       // Remove any gamut checking
       SaveGamutLUT = p ->Gamut;
       p ->Gamut = NULL;

       ChannelsIn   = _cmsChannelsOf(p -> EntryColorSpace);
       ChannelsOut  = _cmsChannelsOf(p -> ExitColorSpace);

       nGridPoints = _cmsReasonableGridpointsByColorspace(p -> EntryColorSpace, dwFlags);

       Grid =  cmsAllocLUT();
       if (!Grid) return NULL;

       Grid = cmsAlloc3DGrid(Grid, nGridPoints, ChannelsIn, ChannelsOut);

       // Compute device link on 16-bit basis
       dwFormatIn   = (CHANNELS_SH(ChannelsIn)|BYTES_SH(2));
       dwFormatOut  = (CHANNELS_SH(ChannelsOut)|BYTES_SH(2));

       SaveFormatIn  = p ->InputFormat;
       SaveFormatOut = p ->OutputFormat;

       p -> InputFormat  = dwFormatIn;
       p -> OutputFormat = dwFormatOut;
       p -> FromInput    = _cmsIdentifyInputFormat(p, dwFormatIn);
       p -> ToOutput     = _cmsIdentifyOutputFormat(p, dwFormatOut);

       // Fix gamut & gamma possible mismatches.

       if (!(dwFlags & cmsFLAGS_NOPRELINEARIZATION)) {

           cmsHTRANSFORM hOne[1];
           hOne[0] = h;

           _cmsComputePrelinearizationTablesFromXFORM(hOne, 1, Grid);
       }

       // Attention to this typecast! we can take the luxury to
       // do this since cmsHTRANSFORM is only an alias to a pointer
       // to the transform struct.

       if (!cmsSample3DGrid(Grid, XFormSampler, (LPVOID) p, Grid -> wFlags)) {

                cmsFreeLUT(Grid);
                Grid = NULL;
       }

       p ->Gamut        = SaveGamutLUT;
       p ->InputFormat  = SaveFormatIn;
       p ->OutputFormat = SaveFormatOut;

       return Grid;
}



// Sampler for Black-preserving CMYK->CMYK transforms

typedef struct {
                cmsHTRANSFORM cmyk2cmyk;
                cmsHTRANSFORM cmyk2Lab;
                LPGAMMATABLE  KTone;
                L16PARAMS     KToneParams;
                LPLUT         LabK2cmyk;
                double        MaxError;

                cmsHTRANSFORM hRoundTrip;
                int           MaxTAC;

                cmsHTRANSFORM hProofOutput;

    } BPCARGO, *LPBPCARGO;



// Preserve black only if that is the only ink used
static
int BlackPreservingGrayOnlySampler(register WORD In[], register WORD Out[], register LPVOID Cargo)
{
    BPCARGO* bp = (LPBPCARGO) Cargo;

    // If going across black only, keep black only
    if (In[0] == 0 && In[1] == 0 && In[2] == 0) {

        // TAC does not apply because it is black ink!
        Out[0] = Out[1] = Out[2] = 0;
        Out[3] = cmsLinearInterpLUT16(In[3], bp->KTone ->GammaTable, &bp->KToneParams);
        return 1;
    }

    // Keep normal transform for other colors
    cmsDoTransform(bp ->cmyk2cmyk, In, Out, 1);
    return 1;
}



// Preserve all K plane.
static
int BlackPreservingSampler(register WORD In[], register WORD Out[], register LPVOID Cargo)
{

    WORD LabK[4];
    double SumCMY, SumCMYK, Error;
    cmsCIELab ColorimetricLab, BlackPreservingLab;
    BPCARGO* bp = (LPBPCARGO) Cargo;

    // Get the K across Tone curve
    LabK[3] = cmsLinearInterpLUT16(In[3], bp->KTone ->GammaTable, &bp->KToneParams);

    // If going across black only, keep black only
    if (In[0] == 0 && In[1] == 0 && In[2] == 0) {

        Out[0] = Out[1] = Out[2] = 0;
        Out[3] = LabK[3];
        return 1;
    }

    // Try the original transform, maybe K is already ok (valid on K=0)
    cmsDoTransform(bp ->cmyk2cmyk, In, Out, 1);
    if (Out[3] == LabK[3]) return 1;


    // No, mesure and keep Lab measurement for further usage
    cmsDoTransform(bp->hProofOutput, Out, &ColorimetricLab, 1);

    // Is not black only and the transform doesn't keep black.
    // Obtain the Lab of CMYK. After that we have Lab + K
    cmsDoTransform(bp ->cmyk2Lab, In, LabK, 1);

    // Obtain the corresponding CMY using reverse interpolation.
    // As a seed, we use the colorimetric CMY
    cmsEvalLUTreverse(bp ->LabK2cmyk, LabK, Out, Out);

    // Estimate the error
    cmsDoTransform(bp->hProofOutput, Out, &BlackPreservingLab, 1);
    Error = cmsDeltaE(&ColorimetricLab, &BlackPreservingLab);


    // Apply TAC if needed

    SumCMY   = Out[0]  + Out[1] + Out[2];
    SumCMYK  = SumCMY + Out[3];

    if (SumCMYK > bp ->MaxTAC) {

        double Ratio = 1 - ((SumCMYK - bp->MaxTAC) / SumCMY);
        if (Ratio < 0)
                  Ratio = 0;

        Out[0] = (WORD) floor(Out[0] * Ratio + 0.5);     // C
        Out[1] = (WORD) floor(Out[1] * Ratio + 0.5);     // M
        Out[2] = (WORD) floor(Out[2] * Ratio + 0.5);     // Y
    }

    return 1;
}


// Sample whole gamut to estimate maximum TAC

#ifdef _MSC_VER
#pragma warning(disable : 4100)
#endif

static
int EstimateTAC(register WORD In[], register WORD Out[], register LPVOID Cargo)
{
    BPCARGO* bp = (LPBPCARGO) Cargo;
    WORD RoundTrip[4];
    int Sum;

    cmsDoTransform(bp->hRoundTrip, In, RoundTrip, 1);

    Sum = RoundTrip[0] + RoundTrip[1] + RoundTrip[2] + RoundTrip[3];

    if (Sum > bp ->MaxTAC)
            bp ->MaxTAC = Sum;

    return 1;
}


// Estimate the maximum error
static
int BlackPreservingEstimateErrorSampler(register WORD In[], register WORD Out[], register LPVOID Cargo)
{
    BPCARGO* bp = (LPBPCARGO) Cargo;
    WORD ColorimetricOut[4];
    cmsCIELab ColorimetricLab, BlackPreservingLab;
    double Error;

    if (In[0] == 0 && In[1] == 0 && In[2] == 0) return 1;

    cmsDoTransform(bp->cmyk2cmyk, In, ColorimetricOut, 1);

    cmsDoTransform(bp->hProofOutput, ColorimetricOut, &ColorimetricLab, 1);
    cmsDoTransform(bp->hProofOutput, Out, &BlackPreservingLab, 1);

    Error = cmsDeltaE(&ColorimetricLab, &BlackPreservingLab);

    if (Error > bp ->MaxError)
        bp ->MaxError = Error;

    return 1;
}

// Setup the K preservation strategy
int LCMSEXPORT cmsSetCMYKPreservationStrategy(int n)
{
    int OldVal = GlobalBlackPreservationStrategy;

    if (n >= 0)
            GlobalBlackPreservationStrategy = n;

    return OldVal;
}

#pragma warning(disable: 4550)

// Get a pointer to callback on depending of strategy
static
_cmsSAMPLER _cmsGetBlackPreservationSampler(void)
{
    switch (GlobalBlackPreservationStrategy) {

        case 0: return BlackPreservingGrayOnlySampler;
        default: return BlackPreservingSampler;
   }

}

// This is the black-preserving devicelink generator
LPLUT _cmsPrecalculateBlackPreservingDeviceLink(cmsHTRANSFORM hCMYK2CMYK, DWORD dwFlags)
{
       _LPcmsTRANSFORM p = (_LPcmsTRANSFORM) hCMYK2CMYK;
       BPCARGO Cargo;
       LPLUT Grid;
       DWORD LocalFlags;
       cmsHPROFILE hLab = cmsCreateLabProfile(NULL);
       int nGridPoints;
       icTagSignature Device2PCS[] = {icSigAToB0Tag,       // Perceptual
                                      icSigAToB1Tag,       // Relative colorimetric
                                      icSigAToB2Tag,       // Saturation
                                      icSigAToB1Tag };     // Absolute colorimetric
                                                           // (Relative/WhitePoint)

       nGridPoints = _cmsReasonableGridpointsByColorspace(p -> EntryColorSpace, dwFlags);

       // Get a copy of inteserting flags for this kind of xform
       LocalFlags = cmsFLAGS_NOTPRECALC;
       if (p -> dwOriginalFlags & cmsFLAGS_BLACKPOINTCOMPENSATION)
           LocalFlags |= cmsFLAGS_BLACKPOINTCOMPENSATION;

       // Fill in cargo struct
       Cargo.cmyk2cmyk = hCMYK2CMYK;

       // Compute tone curve.
       Cargo.KTone  =  _cmsBuildKToneCurve(hCMYK2CMYK, 256);
       if (Cargo.KTone == NULL) return NULL;
       cmsCalcL16Params(Cargo.KTone ->nEntries, &Cargo.KToneParams);


       // Create a CMYK->Lab "normal" transform on input, without K-preservation
       Cargo.cmyk2Lab  = cmsCreateTransform(p ->InputProfile, TYPE_CMYK_16,
                                            hLab, TYPE_Lab_16, p->Intent, LocalFlags);

       // We are going to use the reverse of proof direction
       Cargo.LabK2cmyk = cmsReadICCLut(p->OutputProfile, Device2PCS[p->Intent]);

       // Is there any table available?
       if (Cargo.LabK2cmyk == NULL) {

           Grid = NULL;
           goto Cleanup;
       }

       // Setup a roundtrip on output profile for TAC estimation
       Cargo.hRoundTrip = cmsCreateTransform(p ->OutputProfile, TYPE_CMYK_16,
                                             p ->OutputProfile, TYPE_CMYK_16, p->Intent, cmsFLAGS_NOTPRECALC);


       // Setup a proof CMYK->Lab on output
       Cargo.hProofOutput  = cmsCreateTransform(p ->OutputProfile, TYPE_CMYK_16,
                                            hLab, TYPE_Lab_DBL, p->Intent, LocalFlags);


       // Create an empty LUT for holding K-preserving xform
       Grid =  cmsAllocLUT();
       if (!Grid) goto Cleanup;

       Grid = cmsAlloc3DGrid(Grid, nGridPoints, 4, 4);

       // Setup formatters
       p -> FromInput = _cmsIdentifyInputFormat(p,  TYPE_CMYK_16);
       p -> ToOutput  = _cmsIdentifyOutputFormat(p, TYPE_CMYK_16);



       // Step #1, estimate TAC
       Cargo.MaxTAC = 0;
       if (!cmsSample3DGrid(Grid, EstimateTAC, (LPVOID) &Cargo, 0)) {

                cmsFreeLUT(Grid);
                Grid = NULL;
                goto Cleanup;
       }


       // Step #2, compute approximation
       if (!cmsSample3DGrid(Grid, _cmsGetBlackPreservationSampler(), (LPVOID) &Cargo, 0)) {

                cmsFreeLUT(Grid);
                Grid = NULL;
                goto Cleanup;
       }

       // Step #3, estimate error
        Cargo.MaxError = 0;
        cmsSample3DGrid(Grid, BlackPreservingEstimateErrorSampler, (LPVOID) &Cargo, SAMPLER_INSPECT);


Cleanup:

       if (Cargo.cmyk2Lab) cmsDeleteTransform(Cargo.cmyk2Lab);
       if (Cargo.hRoundTrip) cmsDeleteTransform(Cargo.hRoundTrip);
       if (Cargo.hProofOutput) cmsDeleteTransform(Cargo.hProofOutput);

       if (hLab) cmsCloseProfile(hLab);
       if (Cargo.KTone) cmsFreeGamma(Cargo.KTone);
       if (Cargo.LabK2cmyk) cmsFreeLUT(Cargo.LabK2cmyk);

       return Grid;
}



// Fix broken LUT. just to obtain other CMS compatibility

static
void PatchLUT(LPLUT Grid, WORD At[], WORD Value[],
                     int nChannelsOut, int nChannelsIn)
{
       LPL16PARAMS p16  = &Grid -> CLut16params;
       double     px, py, pz, pw;
       int        x0, y0, z0, w0;
       int        i, index;


       if (Grid ->wFlags & LUT_HASTL1) return;  // There is a prelinearization

       px = ((double) At[0] * (p16->Domain)) / 65535.0;
       py = ((double) At[1] * (p16->Domain)) / 65535.0;
       pz = ((double) At[2] * (p16->Domain)) / 65535.0;
       pw = ((double) At[3] * (p16->Domain)) / 65535.0;

       x0 = (int) floor(px);
       y0 = (int) floor(py);
       z0 = (int) floor(pz);
       w0 = (int) floor(pw);

       if (nChannelsIn == 4) {

              if (((px - x0) != 0) ||
                  ((py - y0) != 0) ||
                  ((pz - z0) != 0) ||
                  ((pw - w0) != 0)) return; // Not on exact node

              index = p16 -> opta4 * x0 +
                      p16 -> opta3 * y0 +
                      p16 -> opta2 * z0 +
                      p16 -> opta1 * w0;
       }
       else
       if (nChannelsIn == 3) {

              if (((px - x0) != 0) ||
                  ((py - y0) != 0) ||
                  ((pz - z0) != 0)) return;  // Not on exact node

              index = p16 -> opta3 * x0 +
                      p16 -> opta2 * y0 +
                      p16 -> opta1 * z0;
       }
       else
       if (nChannelsIn == 1) {

              if (((px - x0) != 0)) return; // Not on exact node

              index = p16 -> opta1 * x0;
       }
       else {
           cmsSignalError(LCMS_ERRC_ABORTED, "(internal) %d Channels are not supported on PatchLUT", nChannelsIn);
           return;
       }

       for (i=0; i < nChannelsOut; i++)
              Grid -> T[index + i] = Value[i];

}



LCMSBOOL _cmsFixWhiteMisalignment(_LPcmsTRANSFORM p)
{

       WORD *WhitePointIn, *WhitePointOut, *BlackPointIn, *BlackPointOut;
       int nOuts, nIns;


       if (!p -> DeviceLink) return FALSE;

       if (p ->Intent == INTENT_ABSOLUTE_COLORIMETRIC) return FALSE;
       if ((p ->PreviewProfile != NULL) &&
           (p ->ProofIntent == INTENT_ABSOLUTE_COLORIMETRIC)) return FALSE;


       if (!_cmsEndPointsBySpace(p -> EntryColorSpace,
                                 &WhitePointIn, &BlackPointIn, &nIns)) return FALSE;


       if (!_cmsEndPointsBySpace(p -> ExitColorSpace,
                                   &WhitePointOut, &BlackPointOut, &nOuts)) return FALSE;

       // Fix white only

       PatchLUT(p -> DeviceLink, WhitePointIn, WhitePointOut, nOuts, nIns);
       // PatchLUT(p -> DeviceLink, BlackPointIn, BlackPointOut, nOuts, nIns);

       return TRUE;
}

