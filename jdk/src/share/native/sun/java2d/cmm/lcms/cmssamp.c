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
//---------------------------------------------------------------------------------
//
//  Little Color Management System
//  Copyright (c) 1998-2010 Marti Maria Saguer
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
//
//---------------------------------------------------------------------------------
//

#include "lcms2_internal.h"



// This file contains routines for resampling and LUT optimization, black point detection
// and black preservation.

// Black point detection -------------------------------------------------------------------------


// PCS -> PCS round trip transform, always uses relative intent on the device -> pcs
static
cmsHTRANSFORM CreateRoundtripXForm(cmsHPROFILE hProfile, cmsUInt32Number nIntent)
{
    cmsHPROFILE hLab = cmsCreateLab4Profile(NULL);
    cmsHTRANSFORM xform;
    cmsBool BPC[4] = { FALSE, FALSE, FALSE, FALSE };
    cmsFloat64Number States[4] = { 1.0, 1.0, 1.0, 1.0 };
    cmsHPROFILE hProfiles[4];
    cmsUInt32Number Intents[4];
    cmsContext ContextID = cmsGetProfileContextID(hProfile);

    hProfiles[0] = hLab; hProfiles[1] = hProfile; hProfiles[2] = hProfile; hProfiles[3] = hLab;
    Intents[0]   = INTENT_RELATIVE_COLORIMETRIC; Intents[1] = nIntent; Intents[2] = INTENT_RELATIVE_COLORIMETRIC; Intents[3] = INTENT_RELATIVE_COLORIMETRIC;

    xform =  cmsCreateExtendedTransform(ContextID, 4, hProfiles, BPC, Intents,
        States, NULL, 0, TYPE_Lab_DBL, TYPE_Lab_DBL, cmsFLAGS_NOCACHE|cmsFLAGS_NOOPTIMIZE);

    cmsCloseProfile(hLab);
    return xform;
}

// Use darker colorants to obtain black point. This works in the relative colorimetric intent and
// assumes more ink results in darker colors. No ink limit is assumed.
static
cmsBool  BlackPointAsDarkerColorant(cmsHPROFILE    hInput,
                                    cmsUInt32Number Intent,
                                    cmsCIEXYZ* BlackPoint,
                                    cmsUInt32Number dwFlags)
{
    cmsUInt16Number *Black;
    cmsHTRANSFORM xform;
    cmsColorSpaceSignature Space;
    cmsUInt32Number nChannels;
    cmsUInt32Number dwFormat;
    cmsHPROFILE hLab;
    cmsCIELab  Lab;
    cmsCIEXYZ  BlackXYZ;
    cmsContext ContextID = cmsGetProfileContextID(hInput);

    // If the profile does not support input direction, assume Black point 0
    if (!cmsIsIntentSupported(hInput, Intent, LCMS_USED_AS_INPUT)) {

        BlackPoint -> X = BlackPoint ->Y = BlackPoint -> Z = 0.0;
        return FALSE;
    }

    // Create a formatter which has n channels and floating point
    dwFormat = cmsFormatterForColorspaceOfProfile(hInput, 2, FALSE);

   // Try to get black by using black colorant
    Space = cmsGetColorSpace(hInput);

    // This function returns darker colorant in 16 bits for several spaces
    if (!_cmsEndPointsBySpace(Space, NULL, &Black, &nChannels)) {

        BlackPoint -> X = BlackPoint ->Y = BlackPoint -> Z = 0.0;
        return FALSE;
    }

    if (nChannels != T_CHANNELS(dwFormat)) {
       BlackPoint -> X = BlackPoint ->Y = BlackPoint -> Z = 0.0;
       return FALSE;
    }

    // Lab will be used as the output space, but lab2 will avoid recursion
    hLab = cmsCreateLab2ProfileTHR(ContextID, NULL);
    if (hLab == NULL) {
       BlackPoint -> X = BlackPoint ->Y = BlackPoint -> Z = 0.0;
       return FALSE;
    }

    // Create the transform
    xform = cmsCreateTransformTHR(ContextID, hInput, dwFormat,
                                hLab, TYPE_Lab_DBL, Intent, cmsFLAGS_NOOPTIMIZE|cmsFLAGS_NOCACHE);
    cmsCloseProfile(hLab);

    if (xform == NULL) {
        // Something went wrong. Get rid of open resources and return zero as black

        BlackPoint -> X = BlackPoint ->Y = BlackPoint -> Z = 0.0;
        return FALSE;
    }

    // Convert black to Lab
    cmsDoTransform(xform, Black, &Lab, 1);

    // Force it to be neutral, clip to max. L* of 50
    Lab.a = Lab.b = 0;
    if (Lab.L > 50) Lab.L = 50;

    // Free the resources
    cmsDeleteTransform(xform);

    // Convert from Lab (which is now clipped) to XYZ.
    cmsLab2XYZ(NULL, &BlackXYZ, &Lab);

    if (BlackPoint != NULL)
        *BlackPoint = BlackXYZ;

    return TRUE;

    cmsUNUSED_PARAMETER(dwFlags);
}

// Get a black point of output CMYK profile, discounting any ink-limiting embedded
// in the profile. For doing that, we use perceptual intent in input direction:
// Lab (0, 0, 0) -> [Perceptual] Profile -> CMYK -> [Rel. colorimetric] Profile -> Lab
static
cmsBool BlackPointUsingPerceptualBlack(cmsCIEXYZ* BlackPoint, cmsHPROFILE hProfile)

{
    cmsHTRANSFORM hRoundTrip;
    cmsCIELab LabIn, LabOut;
    cmsCIEXYZ  BlackXYZ;

     // Is the intent supported by the profile?
    if (!cmsIsIntentSupported(hProfile, INTENT_PERCEPTUAL, LCMS_USED_AS_INPUT)) {

        BlackPoint -> X = BlackPoint ->Y = BlackPoint -> Z = 0.0;
        return TRUE;
    }

    hRoundTrip = CreateRoundtripXForm(hProfile, INTENT_PERCEPTUAL);
    if (hRoundTrip == NULL) {
        BlackPoint -> X = BlackPoint ->Y = BlackPoint -> Z = 0.0;
        return FALSE;
    }

    LabIn.L = LabIn.a = LabIn.b = 0;
    cmsDoTransform(hRoundTrip, &LabIn, &LabOut, 1);

    // Clip Lab to reasonable limits
    if (LabOut.L > 50) LabOut.L = 50;
    LabOut.a = LabOut.b = 0;

    cmsDeleteTransform(hRoundTrip);

    // Convert it to XYZ
    cmsLab2XYZ(NULL, &BlackXYZ, &LabOut);

    if (BlackPoint != NULL)
        *BlackPoint = BlackXYZ;

    return TRUE;
}

// This function shouldn't exist at all -- there is such quantity of broken
// profiles on black point tag, that we must somehow fix chromaticity to
// avoid huge tint when doing Black point compensation. This function does
// just that. There is a special flag for using black point tag, but turned
// off by default because it is bogus on most profiles. The detection algorithm
// involves to turn BP to neutral and to use only L component.

cmsBool CMSEXPORT cmsDetectBlackPoint(cmsCIEXYZ* BlackPoint, cmsHPROFILE hProfile, cmsUInt32Number Intent, cmsUInt32Number dwFlags)
{

    // Zero for black point
    if (cmsGetDeviceClass(hProfile) == cmsSigLinkClass) {

        BlackPoint -> X = BlackPoint ->Y = BlackPoint -> Z = 0.0;
        return FALSE;
    }

    // v4 + perceptual & saturation intents does have its own black point, and it is
    // well specified enough to use it. Black point tag is deprecated in V4.

    if ((cmsGetEncodedICCversion(hProfile) >= 0x4000000) &&
        (Intent == INTENT_PERCEPTUAL || Intent == INTENT_SATURATION)) {

            // Matrix shaper share MRC & perceptual intents
            if (cmsIsMatrixShaper(hProfile))
                return BlackPointAsDarkerColorant(hProfile, INTENT_RELATIVE_COLORIMETRIC, BlackPoint, 0);

            // Get Perceptual black out of v4 profiles. That is fixed for perceptual & saturation intents
            BlackPoint -> X = cmsPERCEPTUAL_BLACK_X;
            BlackPoint -> Y = cmsPERCEPTUAL_BLACK_Y;
            BlackPoint -> Z = cmsPERCEPTUAL_BLACK_Z;

            return TRUE;
    }


#ifdef CMS_USE_PROFILE_BLACK_POINT_TAG

    // v2, v4 rel/abs colorimetric
    if (cmsIsTag(hProfile, cmsSigMediaBlackPointTag) &&
        Intent == INTENT_RELATIVE_COLORIMETRIC) {

            cmsCIEXYZ *BlackPtr, BlackXYZ, UntrustedBlackPoint, TrustedBlackPoint, MediaWhite;
            cmsCIELab Lab;

            // If black point is specified, then use it,

            BlackPtr = cmsReadTag(hProfile, cmsSigMediaBlackPointTag);
            if (BlackPtr != NULL) {

                BlackXYZ = *BlackPtr;
                _cmsReadMediaWhitePoint(&MediaWhite, hProfile);

                // Black point is absolute XYZ, so adapt to D50 to get PCS value
                cmsAdaptToIlluminant(&UntrustedBlackPoint, &MediaWhite, cmsD50_XYZ(), &BlackXYZ);

                // Force a=b=0 to get rid of any chroma
                cmsXYZ2Lab(NULL, &Lab, &UntrustedBlackPoint);
                Lab.a = Lab.b = 0;
                if (Lab.L > 50) Lab.L = 50; // Clip to L* <= 50
                cmsLab2XYZ(NULL, &TrustedBlackPoint, &Lab);

                if (BlackPoint != NULL)
                    *BlackPoint = TrustedBlackPoint;

                return TRUE;
            }
    }
#endif

    // That is about v2 profiles.

    // If output profile, discount ink-limiting and that's all
    if (Intent == INTENT_RELATIVE_COLORIMETRIC &&
        (cmsGetDeviceClass(hProfile) == cmsSigOutputClass) &&
        (cmsGetColorSpace(hProfile)  == cmsSigCmykData))
        return BlackPointUsingPerceptualBlack(BlackPoint, hProfile);

    // Nope, compute BP using current intent.
    return BlackPointAsDarkerColorant(hProfile, Intent, BlackPoint, dwFlags);
}

