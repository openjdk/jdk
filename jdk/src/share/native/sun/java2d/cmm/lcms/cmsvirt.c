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


// Virtual (built-in) profiles
// -----------------------------------------------------------------------------------


// This function creates a profile based on White point, primaries and
// transfer functions.


cmsHPROFILE LCMSEXPORT cmsCreateRGBProfile(LPcmsCIExyY WhitePoint,
                                LPcmsCIExyYTRIPLE Primaries,
                                LPGAMMATABLE TransferFunction[3])
{
       cmsHPROFILE hICC;
       cmsCIEXYZ tmp;
       MAT3 MColorants;
       cmsCIEXYZTRIPLE Colorants;
       cmsCIExyY MaxWhite;


       hICC = _cmsCreateProfilePlaceholder();
       if (!hICC)                          // can't allocate
            return NULL;


       cmsSetDeviceClass(hICC,      icSigDisplayClass);
       cmsSetColorSpace(hICC,       icSigRgbData);
       cmsSetPCS(hICC,              icSigXYZData);
       cmsSetRenderingIntent(hICC,  INTENT_PERCEPTUAL);


       // Implement profile using following tags:
       //
       //  1 icSigProfileDescriptionTag
       //  2 icSigMediaWhitePointTag
       //  3 icSigRedColorantTag
       //  4 icSigGreenColorantTag
       //  5 icSigBlueColorantTag
       //  6 icSigRedTRCTag
       //  7 icSigGreenTRCTag
       //  8 icSigBlueTRCTag

       // This conforms a standard RGB DisplayProfile as says ICC, and then I add

       // 9 icSigChromaticityTag

       // As addendum II


       // Fill-in the tags

       cmsAddTag(hICC, icSigDeviceMfgDescTag,       (LPVOID) "(lcms internal)");
       cmsAddTag(hICC, icSigProfileDescriptionTag,  (LPVOID) "lcms RGB virtual profile");
       cmsAddTag(hICC, icSigDeviceModelDescTag,     (LPVOID) "rgb built-in");


       if (WhitePoint) {

       cmsxyY2XYZ(&tmp, WhitePoint);
       cmsAddTag(hICC, icSigMediaWhitePointTag, (LPVOID) &tmp);
       }

       if (WhitePoint && Primaries) {

        MaxWhite.x =  WhitePoint -> x;
        MaxWhite.y =  WhitePoint -> y;
        MaxWhite.Y =  1.0;

       if (!cmsBuildRGB2XYZtransferMatrix(&MColorants, &MaxWhite, Primaries))
       {
              cmsCloseProfile(hICC);
              return NULL;
       }

       cmsAdaptMatrixToD50(&MColorants, &MaxWhite);

       Colorants.Red.X = MColorants.v[0].n[0];
       Colorants.Red.Y = MColorants.v[1].n[0];
       Colorants.Red.Z = MColorants.v[2].n[0];

       Colorants.Green.X = MColorants.v[0].n[1];
       Colorants.Green.Y = MColorants.v[1].n[1];
       Colorants.Green.Z = MColorants.v[2].n[1];

       Colorants.Blue.X = MColorants.v[0].n[2];
       Colorants.Blue.Y = MColorants.v[1].n[2];
       Colorants.Blue.Z = MColorants.v[2].n[2];

       cmsAddTag(hICC, icSigRedColorantTag,   (LPVOID) &Colorants.Red);
       cmsAddTag(hICC, icSigBlueColorantTag,  (LPVOID) &Colorants.Blue);
       cmsAddTag(hICC, icSigGreenColorantTag, (LPVOID) &Colorants.Green);
       }


       if (TransferFunction) {

       // In case of gamma, we must dup' the table pointer

        cmsAddTag(hICC, icSigRedTRCTag,   (LPVOID) TransferFunction[0]);
        cmsAddTag(hICC, icSigGreenTRCTag, (LPVOID) TransferFunction[1]);
        cmsAddTag(hICC, icSigBlueTRCTag,  (LPVOID) TransferFunction[2]);
       }

       if (Primaries) {
            cmsAddTag(hICC, icSigChromaticityTag, (LPVOID) Primaries);
       }

       return hICC;
}



// This function creates a profile based on White point and transfer function.

cmsHPROFILE   LCMSEXPORT cmsCreateGrayProfile(LPcmsCIExyY WhitePoint,
                                              LPGAMMATABLE TransferFunction)
{
       cmsHPROFILE hICC;
       cmsCIEXYZ tmp;


       hICC = _cmsCreateProfilePlaceholder();
       if (!hICC)                          // can't allocate
            return NULL;


       cmsSetDeviceClass(hICC,      icSigDisplayClass);
       cmsSetColorSpace(hICC,       icSigGrayData);
       cmsSetPCS(hICC,              icSigXYZData);
       cmsSetRenderingIntent(hICC,  INTENT_PERCEPTUAL);



       // Implement profile using following tags:
       //
       //  1 icSigProfileDescriptionTag
       //  2 icSigMediaWhitePointTag
       //  6 icSigGrayTRCTag

       // This conforms a standard Gray DisplayProfile

       // Fill-in the tags


       cmsAddTag(hICC, icSigDeviceMfgDescTag,       (LPVOID) "(lcms internal)");
       cmsAddTag(hICC, icSigProfileDescriptionTag,  (LPVOID) "lcms gray virtual profile");
       cmsAddTag(hICC, icSigDeviceModelDescTag,     (LPVOID) "gray built-in");


       if (WhitePoint) {

       cmsxyY2XYZ(&tmp, WhitePoint);
       cmsAddTag(hICC, icSigMediaWhitePointTag, (LPVOID) &tmp);
       }


       if (TransferFunction) {

       // In case of gamma, we must dup' the table pointer

       cmsAddTag(hICC, icSigGrayTRCTag, (LPVOID) TransferFunction);
       }

       return hICC;

}


static
int IsPCS(icColorSpaceSignature ColorSpace)
{
    return (ColorSpace == icSigXYZData ||
            ColorSpace == icSigLabData);
}

static
void FixColorSpaces(cmsHPROFILE hProfile,
                              icColorSpaceSignature ColorSpace,
                              icColorSpaceSignature PCS,
                              DWORD dwFlags)
{

    if (dwFlags & cmsFLAGS_GUESSDEVICECLASS) {

            if (IsPCS(ColorSpace) && IsPCS(PCS)) {

                    cmsSetDeviceClass(hProfile,      icSigAbstractClass);
                    cmsSetColorSpace(hProfile,       ColorSpace);
                    cmsSetPCS(hProfile,              PCS);
                    return;
            }

            if (IsPCS(ColorSpace) && !IsPCS(PCS)) {

                    cmsSetDeviceClass(hProfile, icSigOutputClass);
                    cmsSetPCS(hProfile,         ColorSpace);
                    cmsSetColorSpace(hProfile,  PCS);
                    return;
            }

            if (IsPCS(PCS) && !IsPCS(ColorSpace)) {

                   cmsSetDeviceClass(hProfile,  icSigInputClass);
                   cmsSetColorSpace(hProfile,   ColorSpace);
                   cmsSetPCS(hProfile,          PCS);
                   return;
            }
    }

    cmsSetDeviceClass(hProfile,      icSigLinkClass);
    cmsSetColorSpace(hProfile,       ColorSpace);
    cmsSetPCS(hProfile,              PCS);

}


static
cmsHPROFILE CreateNamedColorDevicelink(cmsHTRANSFORM xform)
{
    _LPcmsTRANSFORM v = (_LPcmsTRANSFORM) xform;
    cmsHPROFILE hICC;
    cmsCIEXYZ WhitePoint;
    int i, nColors;
    size_t Size;
    LPcmsNAMEDCOLORLIST nc2;


    hICC = _cmsCreateProfilePlaceholder();
    if (hICC == NULL) return NULL;

    cmsSetRenderingIntent(hICC, v -> Intent);
    cmsSetDeviceClass(hICC, icSigNamedColorClass);
    cmsSetColorSpace(hICC, v ->ExitColorSpace);
    cmsSetPCS(hICC, cmsGetPCS(v ->InputProfile));
    cmsTakeMediaWhitePoint(&WhitePoint, v ->InputProfile);

    cmsAddTag(hICC, icSigMediaWhitePointTag,  &WhitePoint);
    cmsAddTag(hICC, icSigDeviceMfgDescTag,       (LPVOID) "LittleCMS");
    cmsAddTag(hICC, icSigProfileDescriptionTag,  (LPVOID) "Named color Device link");
    cmsAddTag(hICC, icSigDeviceModelDescTag,     (LPVOID) "Named color Device link");


    nColors = cmsNamedColorCount(xform);
    nc2     = cmsAllocNamedColorList(nColors);

    Size = sizeof(cmsNAMEDCOLORLIST) + (sizeof(cmsNAMEDCOLOR) * (nColors-1));

    CopyMemory(nc2, v->NamedColorList, Size);
    nc2 ->ColorantCount = _cmsChannelsOf(v ->ExitColorSpace);

    for (i=0; i < nColors; i++) {
        cmsDoTransform(xform, &i, nc2 ->List[i].DeviceColorant, 1);
    }

    cmsAddTag(hICC, icSigNamedColor2Tag, (void*) nc2);
    cmsFreeNamedColorList(nc2);

    return hICC;
}


// Does convert a transform into a device link profile

cmsHPROFILE LCMSEXPORT cmsTransform2DeviceLink(cmsHTRANSFORM hTransform, DWORD dwFlags)
{
    cmsHPROFILE hICC;
    _LPcmsTRANSFORM v = (_LPcmsTRANSFORM) hTransform;
    LPLUT Lut;
    LCMSBOOL MustFreeLUT;
    LPcmsNAMEDCOLORLIST InputColorant = NULL;
    LPcmsNAMEDCOLORLIST OutputColorant = NULL;


    // Check if is a named color transform

    if (cmsGetDeviceClass(v ->InputProfile) == icSigNamedColorClass) {

        return CreateNamedColorDevicelink(hTransform);

    }

    if (v ->DeviceLink) {

        Lut = v -> DeviceLink;
        MustFreeLUT = FALSE;
    }
    else {

        Lut = _cmsPrecalculateDeviceLink(hTransform, dwFlags);
        if (!Lut) return NULL;
        MustFreeLUT = TRUE;
    }

    hICC = _cmsCreateProfilePlaceholder();
    if (!hICC) {                          // can't allocate

        if (MustFreeLUT) cmsFreeLUT(Lut);
        return NULL;
    }


    FixColorSpaces(hICC, v -> EntryColorSpace, v -> ExitColorSpace, dwFlags);

    cmsSetRenderingIntent(hICC,  v -> Intent);

    // Implement devicelink profile using following tags:
    //
    //  1 icSigProfileDescriptionTag
    //  2 icSigMediaWhitePointTag
    //  3 icSigAToB0Tag


    cmsAddTag(hICC, icSigDeviceMfgDescTag,       (LPVOID) "LittleCMS");
    cmsAddTag(hICC, icSigProfileDescriptionTag,  (LPVOID) "Device link");
    cmsAddTag(hICC, icSigDeviceModelDescTag,     (LPVOID) "Device link");


    cmsAddTag(hICC, icSigMediaWhitePointTag,  (LPVOID) cmsD50_XYZ());

    if (cmsGetDeviceClass(hICC) == icSigOutputClass) {

        cmsAddTag(hICC, icSigBToA0Tag, (LPVOID) Lut);
    }
    else
        cmsAddTag(hICC, icSigAToB0Tag, (LPVOID) Lut);



    // Try to read input and output colorant table
    if (cmsIsTag(v ->InputProfile, icSigColorantTableTag)) {

        // Input table can only come in this way.
        InputColorant = cmsReadColorantTable(v ->InputProfile, icSigColorantTableTag);
    }

    // Output is a little bit more complex.
    if (cmsGetDeviceClass(v ->OutputProfile) == icSigLinkClass) {

        // This tag may exist only on devicelink profiles.
        if (cmsIsTag(v ->OutputProfile, icSigColorantTableOutTag)) {

            OutputColorant = cmsReadColorantTable(v ->OutputProfile, icSigColorantTableOutTag);
        }

    } else {

        if (cmsIsTag(v ->OutputProfile, icSigColorantTableTag)) {

            OutputColorant = cmsReadColorantTable(v ->OutputProfile, icSigColorantTableTag);
        }
    }

    if (InputColorant)
           cmsAddTag(hICC, icSigColorantTableTag, InputColorant);

    if (OutputColorant)
           cmsAddTag(hICC, icSigColorantTableOutTag, OutputColorant);



    if (MustFreeLUT) cmsFreeLUT(Lut);
    if (InputColorant) cmsFreeNamedColorList(InputColorant);
    if (OutputColorant) cmsFreeNamedColorList(OutputColorant);

    return hICC;

}


// This is a devicelink operating in the target colorspace with as many transfer
// functions as components

cmsHPROFILE LCMSEXPORT cmsCreateLinearizationDeviceLink(icColorSpaceSignature ColorSpace,
                                                        LPGAMMATABLE TransferFunctions[])
{
       cmsHPROFILE hICC;
       LPLUT Lut;


       hICC = _cmsCreateProfilePlaceholder();
       if (!hICC)                          // can't allocate
            return NULL;


       cmsSetDeviceClass(hICC,      icSigLinkClass);
       cmsSetColorSpace(hICC,       ColorSpace);
       cmsSetPCS(hICC,              ColorSpace);
       cmsSetRenderingIntent(hICC,  INTENT_PERCEPTUAL);


       // Creates a LUT with prelinearization step only
       Lut = cmsAllocLUT();
       if (Lut == NULL) return NULL;

       // Set up channels
       Lut ->InputChan = Lut ->OutputChan = _cmsChannelsOf(ColorSpace);

       // Copy tables to LUT
       cmsAllocLinearTable(Lut, TransferFunctions, 1);

       // Create tags
       cmsAddTag(hICC, icSigDeviceMfgDescTag,       (LPVOID) "(lcms internal)");
       cmsAddTag(hICC, icSigProfileDescriptionTag,  (LPVOID) "lcms linearization device link");
       cmsAddTag(hICC, icSigDeviceModelDescTag,     (LPVOID) "linearization built-in");

       cmsAddTag(hICC, icSigMediaWhitePointTag, (LPVOID) cmsD50_XYZ());
       cmsAddTag(hICC, icSigAToB0Tag, (LPVOID) Lut);

       // LUT is already on virtual profile
       cmsFreeLUT(Lut);

       // Ok, done
       return hICC;
}


// Ink-limiting algorithm
//
//  Sum = C + M + Y + K
//  If Sum > InkLimit
//        Ratio= 1 - (Sum - InkLimit) / (C + M + Y)
//        if Ratio <0
//              Ratio=0
//        endif
//     Else
//         Ratio=1
//     endif
//
//     C = Ratio * C
//     M = Ratio * M
//     Y = Ratio * Y
//     K: Does not change

static
int InkLimitingSampler(register WORD In[], register WORD Out[], register LPVOID Cargo)
{
        double InkLimit = *(double *) Cargo;
        double SumCMY, SumCMYK, Ratio;

        InkLimit = (InkLimit * 655.35);

        SumCMY   = In[0]  + In[1] + In[2];
        SumCMYK  = SumCMY + In[3];

        if (SumCMYK > InkLimit) {

                Ratio = 1 - ((SumCMYK - InkLimit) / SumCMY);
                if (Ratio < 0)
                        Ratio = 0;
        }
        else Ratio = 1;

        Out[0] = (WORD) floor(In[0] * Ratio + 0.5);     // C
        Out[1] = (WORD) floor(In[1] * Ratio + 0.5);     // M
        Out[2] = (WORD) floor(In[2] * Ratio + 0.5);     // Y

        Out[3] = In[3];                                 // K (untouched)

        return TRUE;
}

// This is a devicelink operating in CMYK for ink-limiting

cmsHPROFILE LCMSEXPORT cmsCreateInkLimitingDeviceLink(icColorSpaceSignature ColorSpace,
                                                        double Limit)
{
       cmsHPROFILE hICC;
       LPLUT Lut;

       if (ColorSpace != icSigCmykData) {
            cmsSignalError(LCMS_ERRC_ABORTED, "InkLimiting: Only CMYK currently supported");
            return NULL;
       }

       if (Limit < 0.0 || Limit > 400) {

           cmsSignalError(LCMS_ERRC_WARNING, "InkLimiting: Limit should be between 0..400");
           if (Limit < 0) Limit = 0;
           if (Limit > 400) Limit = 400;

       }

      hICC = _cmsCreateProfilePlaceholder();
       if (!hICC)                          // can't allocate
            return NULL;


       cmsSetDeviceClass(hICC,      icSigLinkClass);
       cmsSetColorSpace(hICC,       ColorSpace);
       cmsSetPCS(hICC,              ColorSpace);
       cmsSetRenderingIntent(hICC,  INTENT_PERCEPTUAL);


       // Creates a LUT with 3D grid only
       Lut = cmsAllocLUT();
       if (Lut == NULL) {
           cmsCloseProfile(hICC);
           return NULL;
           }


       cmsAlloc3DGrid(Lut, 17, _cmsChannelsOf(ColorSpace),
                               _cmsChannelsOf(ColorSpace));

       if (!cmsSample3DGrid(Lut, InkLimitingSampler, (LPVOID) &Limit, 0)) {

                // Shouldn't reach here
                cmsFreeLUT(Lut);
                cmsCloseProfile(hICC);
                return NULL;
       }

       // Create tags

       cmsAddTag(hICC, icSigDeviceMfgDescTag,      (LPVOID) "(lcms internal)");
       cmsAddTag(hICC, icSigProfileDescriptionTag, (LPVOID) "lcms ink limiting device link");
       cmsAddTag(hICC, icSigDeviceModelDescTag,    (LPVOID) "ink limiting built-in");

       cmsAddTag(hICC, icSigMediaWhitePointTag, (LPVOID) cmsD50_XYZ());

       cmsAddTag(hICC, icSigAToB0Tag, (LPVOID) Lut);

       // LUT is already on virtual profile
       cmsFreeLUT(Lut);

       // Ok, done
       return hICC;
}



static
LPLUT Create3x3EmptyLUT(void)
{
        LPLUT AToB0 = cmsAllocLUT();
        if (AToB0 == NULL) return NULL;

        AToB0 -> InputChan = AToB0 -> OutputChan = 3;
        return AToB0;
}



// Creates a fake Lab identity.
cmsHPROFILE LCMSEXPORT cmsCreateLabProfile(LPcmsCIExyY WhitePoint)
{
        cmsHPROFILE hProfile;
        LPLUT Lut;

        hProfile = cmsCreateRGBProfile(WhitePoint == NULL ? cmsD50_xyY() : WhitePoint, NULL, NULL);
        if (hProfile == NULL) return NULL;

        cmsSetDeviceClass(hProfile, icSigAbstractClass);
        cmsSetColorSpace(hProfile,  icSigLabData);
        cmsSetPCS(hProfile,         icSigLabData);

        cmsAddTag(hProfile, icSigDeviceMfgDescTag,     (LPVOID) "(lcms internal)");
        cmsAddTag(hProfile, icSigProfileDescriptionTag, (LPVOID) "lcms Lab identity");
        cmsAddTag(hProfile, icSigDeviceModelDescTag,    (LPVOID) "Lab built-in");


       // An empty LUTs is all we need
       Lut = Create3x3EmptyLUT();
       if (Lut == NULL) {
           cmsCloseProfile(hProfile);
           return NULL;
           }

       cmsAddTag(hProfile, icSigAToB0Tag,    (LPVOID) Lut);
       cmsAddTag(hProfile, icSigBToA0Tag,    (LPVOID) Lut);

       cmsFreeLUT(Lut);

       return hProfile;
}


// Creates a fake Lab identity.
cmsHPROFILE LCMSEXPORT cmsCreateLab4Profile(LPcmsCIExyY WhitePoint)
{
        cmsHPROFILE hProfile;
        LPLUT Lut;

        hProfile = cmsCreateRGBProfile(WhitePoint == NULL ? cmsD50_xyY() : WhitePoint, NULL, NULL);
        if (hProfile == NULL) return NULL;

        cmsSetProfileICCversion(hProfile, 0x4000000);

        cmsSetDeviceClass(hProfile, icSigAbstractClass);
        cmsSetColorSpace(hProfile,  icSigLabData);
        cmsSetPCS(hProfile,         icSigLabData);

        cmsAddTag(hProfile, icSigDeviceMfgDescTag,     (LPVOID) "(lcms internal)");
        cmsAddTag(hProfile, icSigProfileDescriptionTag, (LPVOID) "lcms Lab identity v4");
        cmsAddTag(hProfile, icSigDeviceModelDescTag,    (LPVOID) "Lab v4 built-in");


       // An empty LUTs is all we need
       Lut = Create3x3EmptyLUT();
       if (Lut == NULL) {
           cmsCloseProfile(hProfile);
           return NULL;
           }

       Lut -> wFlags |= LUT_V4_INPUT_EMULATE_V2;
       cmsAddTag(hProfile, icSigAToB0Tag,    (LPVOID) Lut);

       Lut -> wFlags |= LUT_V4_OUTPUT_EMULATE_V2;
       cmsAddTag(hProfile, icSigBToA0Tag,    (LPVOID) Lut);

       cmsFreeLUT(Lut);

       return hProfile;
}



// Creates a fake XYZ identity
cmsHPROFILE LCMSEXPORT cmsCreateXYZProfile(void)
{
        cmsHPROFILE hProfile;
        LPLUT Lut;

        hProfile = cmsCreateRGBProfile(cmsD50_xyY(), NULL, NULL);
        if (hProfile == NULL) return NULL;

        cmsSetDeviceClass(hProfile, icSigAbstractClass);
        cmsSetColorSpace(hProfile, icSigXYZData);
        cmsSetPCS(hProfile,  icSigXYZData);

        cmsAddTag(hProfile, icSigDeviceMfgDescTag,      (LPVOID) "(lcms internal)");
        cmsAddTag(hProfile, icSigProfileDescriptionTag, (LPVOID) "lcms XYZ identity");
        cmsAddTag(hProfile, icSigDeviceModelDescTag,    (LPVOID)  "XYZ built-in");

       // An empty LUTs is all we need
       Lut = Create3x3EmptyLUT();
       if (Lut == NULL) {
           cmsCloseProfile(hProfile);
           return NULL;
           }

       cmsAddTag(hProfile, icSigAToB0Tag,    (LPVOID) Lut);
       cmsAddTag(hProfile, icSigBToA0Tag,    (LPVOID) Lut);
       cmsAddTag(hProfile, icSigPreview0Tag, (LPVOID) Lut);

       cmsFreeLUT(Lut);
       return hProfile;
}



/*

If  R’sRGB,G’sRGB, B’sRGB < 0.04045

    R =  R’sRGB / 12.92
    G =  G’sRGB / 12.92
    B =  B’sRGB / 12.92



else if  R’sRGB,G’sRGB, B’sRGB >= 0.04045

    R = ((R’sRGB + 0.055) / 1.055)^2.4
    G = ((G’sRGB + 0.055) / 1.055)^2.4
    B = ((B’sRGB + 0.055) / 1.055)^2.4

  */

static
LPGAMMATABLE Build_sRGBGamma(void)
{
    double Parameters[5];

    Parameters[0] = 2.4;
    Parameters[1] = 1. / 1.055;
    Parameters[2] = 0.055 / 1.055;
    Parameters[3] = 1. / 12.92;
    Parameters[4] = 0.04045;    // d

    return cmsBuildParametricGamma(1024, 4, Parameters);
}

// Create the ICC virtual profile for sRGB space
cmsHPROFILE LCMSEXPORT cmsCreate_sRGBProfile(void)
{
       cmsCIExyY       D65;
       cmsCIExyYTRIPLE Rec709Primaries = {
                                   {0.6400, 0.3300, 1.0},
                                   {0.3000, 0.6000, 1.0},
                                   {0.1500, 0.0600, 1.0}
                                   };
       LPGAMMATABLE Gamma22[3];
       cmsHPROFILE  hsRGB;

       cmsWhitePointFromTemp(6504, &D65);
       Gamma22[0] = Gamma22[1] = Gamma22[2] = Build_sRGBGamma();

       hsRGB = cmsCreateRGBProfile(&D65, &Rec709Primaries, Gamma22);
       cmsFreeGamma(Gamma22[0]);
       if (hsRGB == NULL) return NULL;


       cmsAddTag(hsRGB, icSigDeviceMfgDescTag,      (LPVOID) "(lcms internal)");
       cmsAddTag(hsRGB, icSigDeviceModelDescTag,    (LPVOID) "sRGB built-in");
       cmsAddTag(hsRGB, icSigProfileDescriptionTag, (LPVOID) "sRGB built-in");

       return hsRGB;
}



typedef struct {
                double Brightness;
                double Contrast;
                double Hue;
                double Saturation;
                cmsCIEXYZ WPsrc, WPdest;

} BCHSWADJUSTS, *LPBCHSWADJUSTS;


static
int bchswSampler(register WORD In[], register WORD Out[], register LPVOID Cargo)
{
    cmsCIELab LabIn, LabOut;
    cmsCIELCh LChIn, LChOut;
    cmsCIEXYZ XYZ;
    LPBCHSWADJUSTS bchsw = (LPBCHSWADJUSTS) Cargo;


    cmsLabEncoded2Float(&LabIn, In);


    cmsLab2LCh(&LChIn, &LabIn);

    // Do some adjusts on LCh

    LChOut.L = LChIn.L * bchsw ->Contrast + bchsw ->Brightness;
    LChOut.C = LChIn.C + bchsw -> Saturation;
    LChOut.h = LChIn.h + bchsw -> Hue;


    cmsLCh2Lab(&LabOut, &LChOut);

    // Move white point in Lab

    cmsLab2XYZ(&bchsw ->WPsrc,  &XYZ, &LabOut);
    cmsXYZ2Lab(&bchsw ->WPdest, &LabOut, &XYZ);

    // Back to encoded

    cmsFloat2LabEncoded(Out, &LabOut);

    return TRUE;
}


// Creates an abstract profile operating in Lab space for Brightness,
// contrast, Saturation and white point displacement

cmsHPROFILE LCMSEXPORT cmsCreateBCHSWabstractProfile(int nLUTPoints,
                                                     double Bright,
                                                     double Contrast,
                                                     double Hue,
                                                     double Saturation,
                                                     int TempSrc,
                                                     int TempDest)
{
     cmsHPROFILE hICC;
     LPLUT Lut;
     BCHSWADJUSTS bchsw;
     cmsCIExyY WhitePnt;

     bchsw.Brightness = Bright;
     bchsw.Contrast   = Contrast;
     bchsw.Hue        = Hue;
     bchsw.Saturation = Saturation;

     cmsWhitePointFromTemp(TempSrc,  &WhitePnt);
     cmsxyY2XYZ(&bchsw.WPsrc, &WhitePnt);

     cmsWhitePointFromTemp(TempDest, &WhitePnt);
     cmsxyY2XYZ(&bchsw.WPdest, &WhitePnt);

      hICC = _cmsCreateProfilePlaceholder();
       if (!hICC)                          // can't allocate
            return NULL;


       cmsSetDeviceClass(hICC,      icSigAbstractClass);
       cmsSetColorSpace(hICC,       icSigLabData);
       cmsSetPCS(hICC,              icSigLabData);

       cmsSetRenderingIntent(hICC,  INTENT_PERCEPTUAL);


       // Creates a LUT with 3D grid only
       Lut = cmsAllocLUT();
       if (Lut == NULL) {
           cmsCloseProfile(hICC);
           return NULL;
           }

       cmsAlloc3DGrid(Lut, nLUTPoints, 3, 3);

       if (!cmsSample3DGrid(Lut, bchswSampler, (LPVOID) &bchsw, 0)) {

                // Shouldn't reach here
                cmsFreeLUT(Lut);
                cmsCloseProfile(hICC);
                return NULL;
       }

       // Create tags

       cmsAddTag(hICC, icSigDeviceMfgDescTag,      (LPVOID) "(lcms internal)");
       cmsAddTag(hICC, icSigProfileDescriptionTag, (LPVOID) "lcms BCHSW abstract profile");
       cmsAddTag(hICC, icSigDeviceModelDescTag,    (LPVOID) "BCHSW built-in");

       cmsAddTag(hICC, icSigMediaWhitePointTag, (LPVOID) cmsD50_XYZ());

       cmsAddTag(hICC, icSigAToB0Tag, (LPVOID) Lut);

       // LUT is already on virtual profile
       cmsFreeLUT(Lut);

       // Ok, done
       return hICC;

}


// Creates a fake NULL profile. This profile return 1 channel as always 0.
// Is useful only for gamut checking tricks

cmsHPROFILE LCMSEXPORT cmsCreateNULLProfile(void)
{
        cmsHPROFILE hProfile;
        LPLUT Lut;
        LPGAMMATABLE EmptyTab;

        hProfile = _cmsCreateProfilePlaceholder();
        if (!hProfile)                          // can't allocate
                return NULL;

        cmsSetDeviceClass(hProfile, icSigOutputClass);
        cmsSetColorSpace(hProfile,  icSigGrayData);
        cmsSetPCS(hProfile,         icSigLabData);


       // An empty LUTs is all we need
       Lut = cmsAllocLUT();
       if (Lut == NULL) {
           cmsCloseProfile(hProfile);
           return NULL;
           }

       Lut -> InputChan = 3;
       Lut -> OutputChan = 1;

       EmptyTab = cmsAllocGamma(2);
       EmptyTab ->GammaTable[0] = 0;
       EmptyTab ->GammaTable[1] = 0;

       cmsAllocLinearTable(Lut, &EmptyTab, 2);

       cmsAddTag(hProfile, icSigBToA0Tag, (LPVOID) Lut);

       cmsFreeLUT(Lut);
       cmsFreeGamma(EmptyTab);

       return hProfile;
}
