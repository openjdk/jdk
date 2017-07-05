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

// Read tags using low-level functions, provides necessary glue code to adapt versions, etc.

// LUT tags
static const cmsTagSignature Device2PCS16[]   =  {cmsSigAToB0Tag,     // Perceptual
                                                  cmsSigAToB1Tag,     // Relative colorimetric
                                                  cmsSigAToB2Tag,     // Saturation
                                                  cmsSigAToB1Tag };   // Absolute colorimetric

static const cmsTagSignature Device2PCSFloat[] = {cmsSigDToB0Tag,     // Perceptual
                                                  cmsSigDToB1Tag,     // Relative colorimetric
                                                  cmsSigDToB2Tag,     // Saturation
                                                  cmsSigDToB3Tag };   // Absolute colorimetric

static const cmsTagSignature PCS2Device16[]    = {cmsSigBToA0Tag,     // Perceptual
                                                  cmsSigBToA1Tag,     // Relative colorimetric
                                                  cmsSigBToA2Tag,     // Saturation
                                                  cmsSigBToA1Tag };   // Absolute colorimetric

static const cmsTagSignature PCS2DeviceFloat[] = {cmsSigBToD0Tag,     // Perceptual
                                                  cmsSigBToD1Tag,     // Relative colorimetric
                                                  cmsSigBToD2Tag,     // Saturation
                                                  cmsSigBToD3Tag };   // Absolute colorimetric


// Factors to convert from 1.15 fixed point to 0..1.0 range and vice-versa
#define InpAdj   (1.0/MAX_ENCODEABLE_XYZ)     // (65536.0/(65535.0*2.0))
#define OutpAdj  (MAX_ENCODEABLE_XYZ)         // ((2.0*65535.0)/65536.0)

// Several resources for gray conversions.
static const cmsFloat64Number GrayInputMatrix[] = { (InpAdj*cmsD50X),  (InpAdj*cmsD50Y),  (InpAdj*cmsD50Z) };
static const cmsFloat64Number OneToThreeInputMatrix[] = { 1, 1, 1 };
static const cmsFloat64Number PickYMatrix[] = { 0, (OutpAdj*cmsD50Y), 0 };
static const cmsFloat64Number PickLstarMatrix[] = { 1, 0, 0 };

// Get a media white point fixing some issues found in certain old profiles
cmsBool  _cmsReadMediaWhitePoint(cmsCIEXYZ* Dest, cmsHPROFILE hProfile)
{
    cmsCIEXYZ* Tag;

    _cmsAssert(Dest != NULL);

    Tag = (cmsCIEXYZ*) cmsReadTag(hProfile, cmsSigMediaWhitePointTag);

    // If no wp, take D50
    if (Tag == NULL) {
        *Dest = *cmsD50_XYZ();
        return TRUE;
    }

    // V2 display profiles should give D50
    if (cmsGetEncodedICCversion(hProfile) < 0x4000000) {

        if (cmsGetDeviceClass(hProfile) == cmsSigDisplayClass) {
            *Dest = *cmsD50_XYZ();
            return TRUE;
        }
    }

    // All seems ok
    *Dest = *Tag;
    return TRUE;
}


// Chromatic adaptation matrix. Fix some issues as well
cmsBool  _cmsReadCHAD(cmsMAT3* Dest, cmsHPROFILE hProfile)
{
    cmsMAT3* Tag;

    _cmsAssert(Dest != NULL);

    Tag = (cmsMAT3*) cmsReadTag(hProfile, cmsSigChromaticAdaptationTag);

    if (Tag != NULL) {

        *Dest = *Tag;
        return TRUE;
    }

    // No CHAD available, default it to identity
    _cmsMAT3identity(Dest);

    // V2 display profiles should give D50
    if (cmsGetEncodedICCversion(hProfile) < 0x4000000) {

        if (cmsGetDeviceClass(hProfile) == cmsSigDisplayClass) {

            cmsCIEXYZ* White = (cmsCIEXYZ*) cmsReadTag(hProfile, cmsSigMediaWhitePointTag);

            if (White == NULL) {

                _cmsMAT3identity(Dest);
                return TRUE;
            }

            return _cmsAdaptationMatrix(Dest, NULL, cmsD50_XYZ(), White);
        }
    }

    return TRUE;
}


// Auxiliar, read colorants as a MAT3 structure. Used by any function that needs a matrix-shaper
static
cmsBool ReadICCMatrixRGB2XYZ(cmsMAT3* r, cmsHPROFILE hProfile)
{
    cmsCIEXYZ *PtrRed, *PtrGreen, *PtrBlue;

    _cmsAssert(r != NULL);

    PtrRed   = (cmsCIEXYZ *) cmsReadTag(hProfile, cmsSigRedColorantTag);
    PtrGreen = (cmsCIEXYZ *) cmsReadTag(hProfile, cmsSigGreenColorantTag);
    PtrBlue  = (cmsCIEXYZ *) cmsReadTag(hProfile, cmsSigBlueColorantTag);

    if (PtrRed == NULL || PtrGreen == NULL || PtrBlue == NULL)
        return FALSE;

    _cmsVEC3init(&r -> v[0], PtrRed -> X, PtrGreen -> X,  PtrBlue -> X);
    _cmsVEC3init(&r -> v[1], PtrRed -> Y, PtrGreen -> Y,  PtrBlue -> Y);
    _cmsVEC3init(&r -> v[2], PtrRed -> Z, PtrGreen -> Z,  PtrBlue -> Z);

    return TRUE;
}


// Gray input pipeline
static
cmsPipeline* BuildGrayInputMatrixPipeline(cmsHPROFILE hProfile)
{
    cmsToneCurve *GrayTRC;
    cmsPipeline* Lut;
    cmsContext ContextID = cmsGetProfileContextID(hProfile);

    GrayTRC = (cmsToneCurve *) cmsReadTag(hProfile, cmsSigGrayTRCTag);
    if (GrayTRC == NULL) return NULL;

    Lut = cmsPipelineAlloc(ContextID, 1, 3);
    if (Lut == NULL) return NULL;

    if (cmsGetPCS(hProfile) == cmsSigLabData) {

        // In this case we implement the profile as an  identity matrix plus 3 tone curves
        cmsUInt16Number Zero[2] = { 0x8080, 0x8080 };
        cmsToneCurve* EmptyTab;
        cmsToneCurve* LabCurves[3];

        EmptyTab = cmsBuildTabulatedToneCurve16(ContextID, 2, Zero);

        if (EmptyTab == NULL) {

                 cmsPipelineFree(Lut);
                 return NULL;
        }

        LabCurves[0] = GrayTRC;
        LabCurves[1] = EmptyTab;
        LabCurves[2] = EmptyTab;

        cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocMatrix(ContextID, 3,  1, OneToThreeInputMatrix, NULL));
        cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocToneCurves(ContextID, 3, LabCurves));

        cmsFreeToneCurve(EmptyTab);

    }
    else  {
       cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocToneCurves(ContextID, 1, &GrayTRC));
       cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocMatrix(ContextID, 3,  1, GrayInputMatrix, NULL));
    }

    return Lut;
}

// RGB Matrix shaper
static
cmsPipeline* BuildRGBInputMatrixShaper(cmsHPROFILE hProfile)
{
    cmsPipeline* Lut;
    cmsMAT3 Mat;
    cmsToneCurve *Shapes[3];
    cmsContext ContextID = cmsGetProfileContextID(hProfile);
    int i, j;

    if (!ReadICCMatrixRGB2XYZ(&Mat, hProfile)) return NULL;

    // XYZ PCS in encoded in 1.15 format, and the matrix output comes in 0..0xffff range, so
    // we need to adjust the output by a factor of (0x10000/0xffff) to put data in
    // a 1.16 range, and then a >> 1 to obtain 1.15. The total factor is (65536.0)/(65535.0*2)

    for (i=0; i < 3; i++)
        for (j=0; j < 3; j++)
            Mat.v[i].n[j] *= InpAdj;


    Shapes[0] = (cmsToneCurve *) cmsReadTag(hProfile, cmsSigRedTRCTag);
    Shapes[1] = (cmsToneCurve *) cmsReadTag(hProfile, cmsSigGreenTRCTag);
    Shapes[2] = (cmsToneCurve *) cmsReadTag(hProfile, cmsSigBlueTRCTag);

    if (!Shapes[0] || !Shapes[1] || !Shapes[2])
        return NULL;

    Lut = cmsPipelineAlloc(ContextID, 3, 3);
    if (Lut != NULL) {

        cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocToneCurves(ContextID, 3, Shapes));
        cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocMatrix(ContextID, 3, 3, (cmsFloat64Number*) &Mat, NULL));
    }

    return Lut;
}

// Read and create a BRAND NEW MPE LUT from a given profile. All stuff dependent of version, etc
// is adjusted here in order to create a LUT that takes care of all those details
cmsPipeline* _cmsReadInputLUT(cmsHPROFILE hProfile, int Intent)
{
    cmsTagTypeSignature OriginalType;
    cmsTagSignature tag16    = Device2PCS16[Intent];
    cmsTagSignature tagFloat = Device2PCSFloat[Intent];
    cmsContext ContextID = cmsGetProfileContextID(hProfile);

    if (cmsIsTag(hProfile, tagFloat)) {  // Float tag takes precedence

        // Floating point LUT are always V4, so no adjustment is required
        return cmsPipelineDup((cmsPipeline*) cmsReadTag(hProfile, tagFloat));
    }

    // Revert to perceptual if no tag is found
    if (!cmsIsTag(hProfile, tag16)) {
        tag16 = Device2PCS16[0];
    }

    if (cmsIsTag(hProfile, tag16)) { // Is there any LUT-Based table?

        // Check profile version and LUT type. Do the necessary adjustments if needed

        // First read the tag
        cmsPipeline* Lut = (cmsPipeline*) cmsReadTag(hProfile, tag16);
        if (Lut == NULL) return NULL;

        // After reading it, we have now info about the original type
        OriginalType =  _cmsGetTagTrueType(hProfile, tag16);

        // The profile owns the Lut, so we need to copy it
        Lut = cmsPipelineDup(Lut);

        // We need to adjust data only for Lab16 on output
        if (OriginalType != cmsSigLut16Type || cmsGetPCS(hProfile) != cmsSigLabData)
            return Lut;

        // Add a matrix for conversion V2 to V4 Lab PCS
        cmsPipelineInsertStage(Lut, cmsAT_END, _cmsStageAllocLabV2ToV4(ContextID));
        return Lut;
    }

    // Lut was not found, try to create a matrix-shaper

    // Check if this is a grayscale profile.
    if (cmsGetColorSpace(hProfile) == cmsSigGrayData) {

        // if so, build appropiate conversion tables.
        // The tables are the PCS iluminant, scaled across GrayTRC
        return BuildGrayInputMatrixPipeline(hProfile);
    }

    // Not gray, create a normal matrix-shaper
    return BuildRGBInputMatrixShaper(hProfile);
}

// ---------------------------------------------------------------------------------------------------------------

// Gray output pipeline.
// XYZ -> Gray or Lab -> Gray. Since we only know the GrayTRC, we need to do some assumptions. Gray component will be
// given by Y on XYZ PCS and by L* on Lab PCS, Both across inverse TRC curve.
// The complete pipeline on XYZ is Matrix[3:1] -> Tone curve and in Lab Matrix[3:1] -> Tone Curve as well.

static
cmsPipeline* BuildGrayOutputPipeline(cmsHPROFILE hProfile)
{
    cmsToneCurve *GrayTRC, *RevGrayTRC;
    cmsPipeline* Lut;
    cmsContext ContextID = cmsGetProfileContextID(hProfile);

    GrayTRC = (cmsToneCurve *) cmsReadTag(hProfile, cmsSigGrayTRCTag);
    if (GrayTRC == NULL) return NULL;

    RevGrayTRC = cmsReverseToneCurve(GrayTRC);
    if (RevGrayTRC == NULL) return NULL;

    Lut = cmsPipelineAlloc(ContextID, 3, 1);
    if (Lut == NULL) {
        cmsFreeToneCurve(RevGrayTRC);
        return NULL;
    }

    if (cmsGetPCS(hProfile) == cmsSigLabData) {

        cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocMatrix(ContextID, 1,  3, PickLstarMatrix, NULL));
    }
    else  {
        cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocMatrix(ContextID, 1,  3, PickYMatrix, NULL));
    }

    cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocToneCurves(ContextID, 1, &RevGrayTRC));
    cmsFreeToneCurve(RevGrayTRC);

    return Lut;
}




static
cmsPipeline* BuildRGBOutputMatrixShaper(cmsHPROFILE hProfile)
{
    cmsPipeline* Lut;
    cmsToneCurve *Shapes[3], *InvShapes[3];
    cmsMAT3 Mat, Inv;
    int i, j;
    cmsContext ContextID = cmsGetProfileContextID(hProfile);

    if (!ReadICCMatrixRGB2XYZ(&Mat, hProfile))
        return NULL;

    if (!_cmsMAT3inverse(&Mat, &Inv))
        return NULL;

    // XYZ PCS in encoded in 1.15 format, and the matrix input should come in 0..0xffff range, so
    // we need to adjust the input by a << 1 to obtain a 1.16 fixed and then by a factor of
    // (0xffff/0x10000) to put data in 0..0xffff range. Total factor is (2.0*65535.0)/65536.0;

    for (i=0; i < 3; i++)
        for (j=0; j < 3; j++)
            Inv.v[i].n[j] *= OutpAdj;

    Shapes[0] = (cmsToneCurve *) cmsReadTag(hProfile, cmsSigRedTRCTag);
    Shapes[1] = (cmsToneCurve *) cmsReadTag(hProfile, cmsSigGreenTRCTag);
    Shapes[2] = (cmsToneCurve *) cmsReadTag(hProfile, cmsSigBlueTRCTag);

    if (!Shapes[0] || !Shapes[1] || !Shapes[2])
        return NULL;

    InvShapes[0] = cmsReverseToneCurve(Shapes[0]);
    InvShapes[1] = cmsReverseToneCurve(Shapes[1]);
    InvShapes[2] = cmsReverseToneCurve(Shapes[2]);

    if (!InvShapes[0] || !InvShapes[1] || !InvShapes[2]) {
        return NULL;
    }

    Lut = cmsPipelineAlloc(ContextID, 3, 3);
    if (Lut != NULL) {

        cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocMatrix(ContextID, 3, 3, (cmsFloat64Number*) &Inv, NULL));
        cmsPipelineInsertStage(Lut, cmsAT_END, cmsStageAllocToneCurves(ContextID, 3, InvShapes));
    }

    cmsFreeToneCurveTriple(InvShapes);
    return Lut;
}

// Create an output MPE LUT from agiven profile. Version mismatches are handled here
cmsPipeline* _cmsReadOutputLUT(cmsHPROFILE hProfile, int Intent)
{
    cmsTagTypeSignature OriginalType;
    cmsTagSignature tag16    = PCS2Device16[Intent];
    cmsTagSignature tagFloat = PCS2DeviceFloat[Intent];
    cmsContext ContextID     = cmsGetProfileContextID(hProfile);

    if (cmsIsTag(hProfile, tagFloat)) {  // Float tag takes precedence

        // Floating point LUT are always V4, so no adjustment is required
        return cmsPipelineDup((cmsPipeline*) cmsReadTag(hProfile, tagFloat));
    }

    // Revert to perceptual if no tag is found
    if (!cmsIsTag(hProfile, tag16)) {
        tag16 = PCS2Device16[0];
    }

    if (cmsIsTag(hProfile, tag16)) { // Is there any LUT-Based table?

        // Check profile version and LUT type. Do the necessary adjustments if needed

        // First read the tag
        cmsPipeline* Lut = (cmsPipeline*) cmsReadTag(hProfile, tag16);
        if (Lut == NULL) return NULL;

        // After reading it, we have info about the original type
        OriginalType =  _cmsGetTagTrueType(hProfile, tag16);

        // The profile owns the Lut, so we need to copy it
        Lut = cmsPipelineDup(Lut);

        // We need to adjust data only for Lab and Lut16 type
        if (OriginalType != cmsSigLut16Type || cmsGetPCS(hProfile) != cmsSigLabData)
            return Lut;

        // Add a matrix for conversion V4 to V2 Lab PCS
        cmsPipelineInsertStage(Lut, cmsAT_BEGIN, _cmsStageAllocLabV4ToV2(ContextID));
        return Lut;
    }

    // Lut not found, try to create a matrix-shaper

    // Check if this is a grayscale profile.
     if (cmsGetColorSpace(hProfile) == cmsSigGrayData) {

              // if so, build appropiate conversion tables.
              // The tables are the PCS iluminant, scaled across GrayTRC
              return BuildGrayOutputPipeline(hProfile);
    }

    // Not gray, create a normal matrix-shaper
    return BuildRGBOutputMatrixShaper(hProfile);
}

// ---------------------------------------------------------------------------------------------------------------

// This one includes abstract profiles as well. Matrix-shaper cannot be obtained on that device class. The
// tag name here may default to AToB0
cmsPipeline* _cmsReadDevicelinkLUT(cmsHPROFILE hProfile, int Intent)
{
    cmsPipeline* Lut;
    cmsTagTypeSignature OriginalType;
    cmsTagSignature tag16    = Device2PCS16[Intent];
    cmsTagSignature tagFloat = Device2PCSFloat[Intent];
    cmsContext ContextID = cmsGetProfileContextID(hProfile);

    if (cmsIsTag(hProfile, tagFloat)) {  // Float tag takes precedence

        // Floating point LUT are always V4, no adjustment is required
        return cmsPipelineDup((cmsPipeline*) cmsReadTag(hProfile, tagFloat));
    }

    tagFloat = Device2PCSFloat[0];
    if (cmsIsTag(hProfile, tagFloat)) {

        return cmsPipelineDup((cmsPipeline*) cmsReadTag(hProfile, tagFloat));
    }

    if (!cmsIsTag(hProfile, tag16)) {  // Is there any LUT-Based table?

        tag16    = Device2PCS16[0];
        if (!cmsIsTag(hProfile, tag16)) return NULL;
    }

    // Check profile version and LUT type. Do the necessary adjustments if needed

    // Read the tag
    Lut = (cmsPipeline*) cmsReadTag(hProfile, tag16);
    if (Lut == NULL) return NULL;

    // The profile owns the Lut, so we need to copy it
    Lut = cmsPipelineDup(Lut);

    // After reading it, we have info about the original type
    OriginalType =  _cmsGetTagTrueType(hProfile, tag16);

    // We need to adjust data for Lab16 on output
    if (OriginalType != cmsSigLut16Type) return Lut;

    // Here it is possible to get Lab on both sides

    if (cmsGetPCS(hProfile) == cmsSigLabData) {
            cmsPipelineInsertStage(Lut, cmsAT_BEGIN, _cmsStageAllocLabV4ToV2(ContextID));
    }

    if (cmsGetColorSpace(hProfile) == cmsSigLabData) {
            cmsPipelineInsertStage(Lut, cmsAT_END, _cmsStageAllocLabV2ToV4(ContextID));
    }

    return Lut;


}

// ---------------------------------------------------------------------------------------------------------------

// Returns TRUE if the profile is implemented as matrix-shaper
cmsBool  CMSEXPORT cmsIsMatrixShaper(cmsHPROFILE hProfile)
{
    switch (cmsGetColorSpace(hProfile)) {

    case cmsSigGrayData:

        return cmsIsTag(hProfile, cmsSigGrayTRCTag);

    case cmsSigRgbData:

        return (cmsIsTag(hProfile, cmsSigRedColorantTag) &&
                cmsIsTag(hProfile, cmsSigGreenColorantTag) &&
                cmsIsTag(hProfile, cmsSigBlueColorantTag) &&
                cmsIsTag(hProfile, cmsSigRedTRCTag) &&
                cmsIsTag(hProfile, cmsSigGreenTRCTag) &&
                cmsIsTag(hProfile, cmsSigBlueTRCTag));

    default:

        return FALSE;
    }
}

// Returns TRUE if the intent is implemented as CLUT
cmsBool  CMSEXPORT cmsIsCLUT(cmsHPROFILE hProfile, cmsUInt32Number Intent, int UsedDirection)
{
    const cmsTagSignature* TagTable;

    // For devicelinks, the supported intent is that one stated in the header
    if (cmsGetDeviceClass(hProfile) == cmsSigLinkClass) {
            return (cmsGetHeaderRenderingIntent(hProfile) == Intent);
    }

    switch (UsedDirection) {

       case LCMS_USED_AS_INPUT: TagTable = Device2PCS16; break;
       case LCMS_USED_AS_OUTPUT:TagTable = PCS2Device16; break;

       // For proofing, we need rel. colorimetric in output. Let's do some recursion
       case LCMS_USED_AS_PROOF:
           return cmsIsIntentSupported(hProfile, Intent, LCMS_USED_AS_INPUT) &&
                  cmsIsIntentSupported(hProfile, INTENT_RELATIVE_COLORIMETRIC, LCMS_USED_AS_OUTPUT);

       default:
           cmsSignalError(cmsGetProfileContextID(hProfile), cmsERROR_RANGE, "Unexpected direction (%d)", UsedDirection);
           return FALSE;
    }

    return cmsIsTag(hProfile, TagTable[Intent]);

}


// Return info about supported intents
cmsBool  CMSEXPORT cmsIsIntentSupported(cmsHPROFILE hProfile,
                                        cmsUInt32Number Intent, int UsedDirection)
{

    if (cmsIsCLUT(hProfile, Intent, UsedDirection)) return TRUE;

    // Is there any matrix-shaper? If so, the intent is supported. This is a bit odd, since V2 matrix shaper
    // does not fully support relative colorimetric because they cannot deal with non-zero black points, but
    // many profiles claims that, and this is certainly not true for V4 profiles. Lets answer "yes" no matter
    // the accuracy would be less than optimal in rel.col and v2 case.

    return cmsIsMatrixShaper(hProfile);
}


// ---------------------------------------------------------------------------------------------------------------

// Read both, profile sequence description and profile sequence id if present. Then combine both to
// create qa unique structure holding both. Shame on ICC to store things in such complicated way.

cmsSEQ* _cmsReadProfileSequence(cmsHPROFILE hProfile)
{
    cmsSEQ* ProfileSeq;
    cmsSEQ* ProfileId;
    cmsSEQ* NewSeq;
    cmsUInt32Number i;

    // Take profile sequence description first
    ProfileSeq = (cmsSEQ*) cmsReadTag(hProfile, cmsSigProfileSequenceDescTag);

    // Take profile sequence ID
    ProfileId  = (cmsSEQ*) cmsReadTag(hProfile, cmsSigProfileSequenceIdTag);

    if (ProfileSeq == NULL && ProfileId == NULL) return NULL;

    if (ProfileSeq == NULL) return cmsDupProfileSequenceDescription(ProfileId);
    if (ProfileId  == NULL) return cmsDupProfileSequenceDescription(ProfileSeq);

    // We have to mix both together. For that they must agree
    if (ProfileSeq ->n != ProfileId ->n) return cmsDupProfileSequenceDescription(ProfileSeq);

    NewSeq = cmsDupProfileSequenceDescription(ProfileSeq);

    // Ok, proceed to the mixing
    for (i=0; i < ProfileSeq ->n; i++) {

        memmove(&NewSeq ->seq[i].ProfileID, &ProfileId ->seq[i].ProfileID, sizeof(cmsProfileID));
        NewSeq ->seq[i].Description = cmsMLUdup(ProfileId ->seq[i].Description);
    }

    return NewSeq;
}

// Dump the contents of profile sequence in both tags (if v4 available)
cmsBool _cmsWriteProfileSequence(cmsHPROFILE hProfile, const cmsSEQ* seq)
{
    if (!cmsWriteTag(hProfile, cmsSigProfileSequenceDescTag, seq)) return FALSE;

    if (cmsGetProfileVersion(hProfile) >= 4.0) {

            if (!cmsWriteTag(hProfile, cmsSigProfileSequenceIdTag, seq)) return FALSE;
    }

    return TRUE;
}


// Auxiliar, read and duplicate a MLU if found.
static
cmsMLU* GetMLUFromProfile(cmsHPROFILE h, cmsTagSignature sig)
{
    cmsMLU* mlu = (cmsMLU*) cmsReadTag(h, sig);
    if (mlu == NULL) return NULL;

    return cmsMLUdup(mlu);
}

// Create a sequence description out of an array of profiles
cmsSEQ* _cmsCompileProfileSequence(cmsContext ContextID, cmsUInt32Number nProfiles, cmsHPROFILE hProfiles[])
{
    cmsUInt32Number i;
    cmsSEQ* seq = cmsAllocProfileSequenceDescription(ContextID, nProfiles);

    if (seq == NULL) return NULL;

    for (i=0; i < nProfiles; i++) {

        cmsPSEQDESC* ps = &seq ->seq[i];
        cmsHPROFILE h = hProfiles[i];
        cmsTechnologySignature* techpt;

        cmsGetHeaderAttributes(h, &ps ->attributes);
        cmsGetHeaderProfileID(h, ps ->ProfileID.ID8);
        ps ->deviceMfg   = cmsGetHeaderManufacturer(h);
        ps ->deviceModel = cmsGetHeaderModel(h);

        techpt = (cmsTechnologySignature*) cmsReadTag(h, cmsSigTechnologyTag);
        if (techpt == NULL)
            ps ->technology   =  (cmsTechnologySignature) 0;
        else
            ps ->technology   = *techpt;

        ps ->Manufacturer = GetMLUFromProfile(h,  cmsSigDeviceMfgDescTag);
        ps ->Model        = GetMLUFromProfile(h,  cmsSigDeviceModelDescTag);
        ps ->Description  = GetMLUFromProfile(h, cmsSigProfileDescriptionTag);

    }

    return seq;
}

// -------------------------------------------------------------------------------------------------------------------


static
const cmsMLU* GetInfo(cmsHPROFILE hProfile, cmsInfoType Info)
{
    cmsTagSignature sig;

    switch (Info) {

    case cmsInfoDescription:
        sig = cmsSigProfileDescriptionTag;
        break;

    case cmsInfoManufacturer:
        sig = cmsSigDeviceMfgDescTag;
        break;

    case cmsInfoModel:
        sig = cmsSigDeviceModelDescTag;
         break;

    case cmsInfoCopyright:
        sig = cmsSigCopyrightTag;
        break;

    default: return NULL;
    }


    return (cmsMLU*) cmsReadTag(hProfile, sig);
}



cmsUInt32Number CMSEXPORT cmsGetProfileInfo(cmsHPROFILE hProfile, cmsInfoType Info,
                                            const char LanguageCode[3], const char CountryCode[3],
                                            wchar_t* Buffer, cmsUInt32Number BufferSize)
{
    const cmsMLU* mlu = GetInfo(hProfile, Info);
    if (mlu == NULL) return 0;

    return cmsMLUgetWide(mlu, LanguageCode, CountryCode, Buffer, BufferSize);
}


cmsUInt32Number  CMSEXPORT cmsGetProfileInfoASCII(cmsHPROFILE hProfile, cmsInfoType Info,
                                                          const char LanguageCode[3], const char CountryCode[3],
                                                          char* Buffer, cmsUInt32Number BufferSize)
{
    const cmsMLU* mlu = GetInfo(hProfile, Info);
    if (mlu == NULL) return 0;

    return cmsMLUgetASCII(mlu, LanguageCode, CountryCode, Buffer, BufferSize);
}
