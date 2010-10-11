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

// Transformations stuff
// -----------------------------------------------------------------------

// Alarm codes for 16-bit transformations, because the fixed range of containers there are
// no values left to mark out of gamut. volatile is C99 per 6.2.5
static volatile cmsUInt16Number Alarm[cmsMAXCHANNELS];
static volatile cmsFloat64Number GlobalAdaptationState = 0;

// The adaptation state may be defaulted by this function. If you don't like it, use the extended transform routine
cmsFloat64Number CMSEXPORT cmsSetAdaptationState(cmsFloat64Number d)
{
    cmsFloat64Number OldVal = GlobalAdaptationState;

    if (d >= 0)
        GlobalAdaptationState = d;

    return OldVal;
}

// Alarm codes are always global
void CMSEXPORT cmsSetAlarmCodes(cmsUInt16Number NewAlarm[cmsMAXCHANNELS])
{
    int i;

    _cmsAssert(NewAlarm != NULL);

    for (i=0; i < cmsMAXCHANNELS; i++)
        Alarm[i] = NewAlarm[i];
}

// You can get the codes cas well
void CMSEXPORT cmsGetAlarmCodes(cmsUInt16Number OldAlarm[cmsMAXCHANNELS])
{
    int i;

    _cmsAssert(OldAlarm != NULL);

    for (i=0; i < cmsMAXCHANNELS; i++)
        OldAlarm[i] = Alarm[i];
}

// Get rid of transform resources
void CMSEXPORT cmsDeleteTransform(cmsHTRANSFORM hTransform)
{
    _cmsTRANSFORM* p = (_cmsTRANSFORM*) hTransform;

    _cmsAssert(p != NULL);

    if (p -> GamutCheck)
        cmsPipelineFree(p -> GamutCheck);

    if (p -> Lut)
        cmsPipelineFree(p -> Lut);

    if (p ->InputColorant)
        cmsFreeNamedColorList(p ->InputColorant);

    if (p -> OutputColorant)
        cmsFreeNamedColorList(p ->OutputColorant);

    if (p ->Sequence)
        cmsFreeProfileSequenceDescription(p ->Sequence);

    LCMS_FREE_LOCK(&p->rwlock);
    _cmsFree(p ->ContextID, (void *) p);
}

// Apply transform
void CMSEXPORT cmsDoTransform(cmsHTRANSFORM  Transform,
                              const void* InputBuffer,
                              void* OutputBuffer,
                              cmsUInt32Number Size)

{
    _cmsTRANSFORM* p = (_cmsTRANSFORM*) Transform;

    p -> xform(p, InputBuffer, OutputBuffer, Size);
}


// Transform routines ----------------------------------------------------------------------------------------------------------

// Float xform converts floats. Since there are no performance issues, one routine does all job, including gamut check.
// Note that because extended range, we can use a -1.0 value for out of gamut in this case.
static
void FloatXFORM(_cmsTRANSFORM* p,
                const void* in,
                void* out, cmsUInt32Number Size)
{
    cmsUInt8Number* accum;
    cmsUInt8Number* output;
    cmsFloat32Number fIn[cmsMAXCHANNELS], fOut[cmsMAXCHANNELS];
    cmsFloat32Number OutOfGamut;
    cmsUInt32Number i, j;

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;

    for (i=0; i < Size; i++) {

        accum = p -> FromInputFloat(p, fIn, accum, Size);

        // Any gamut chack to do?
        if (p ->GamutCheck != NULL) {

            // Evaluate gamut marker.
            cmsPipelineEvalFloat( fIn, &OutOfGamut, p ->GamutCheck);

            // Is current color out of gamut?
            if (OutOfGamut > 0.0) {

                // Certainly, out of gamut
                for (j=0; j < cmsMAXCHANNELS; j++)
                    fOut[j] = -1.0;

            }
            else {
                // No, proceed normally
                cmsPipelineEvalFloat(fIn, fOut, p -> Lut);
            }
        }
        else {

            // No gamut check at all
            cmsPipelineEvalFloat(fIn, fOut, p -> Lut);
        }

        // Back to asked representation
        output = p -> ToOutputFloat(p, fOut, output, Size);
    }
}

// 16 bit precision -----------------------------------------------------------------------------------------------------------

// Null transformation, only applies formatters. No caché
static
void NullXFORM(_cmsTRANSFORM* p,
               const void* in,
               void* out, cmsUInt32Number Size)
{
    cmsUInt8Number* accum;
    cmsUInt8Number* output;
    cmsUInt16Number wIn[cmsMAXCHANNELS];
    cmsUInt32Number i, n;

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;
    n = Size;                    // Buffer len

    for (i=0; i < n; i++) {

        accum  = p -> FromInput(p, wIn, accum, Size);
        output = p -> ToOutput(p, wIn, output, Size);
    }
}


// No gamut check, no cache, 16 bits
static
void PrecalculatedXFORM(_cmsTRANSFORM* p,
                        const void* in,
                        void* out, cmsUInt32Number Size)
{
    register cmsUInt8Number* accum;
    register cmsUInt8Number* output;
    cmsUInt16Number wIn[cmsMAXCHANNELS], wOut[cmsMAXCHANNELS];
    cmsUInt32Number i, n;

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;
    n = Size;

    for (i=0; i < n; i++) {

        accum = p -> FromInput(p, wIn, accum, Size);
        p ->Lut ->Eval16Fn(wIn, wOut, p -> Lut->Data);
        output = p -> ToOutput(p, wOut, output, Size);
    }
}


// Auxiliar: Handle precalculated gamut check
static
void TransformOnePixelWithGamutCheck(_cmsTRANSFORM* p,
                                     const cmsUInt16Number wIn[],
                                     cmsUInt16Number wOut[])
{
    cmsUInt16Number wOutOfGamut;

    p ->GamutCheck ->Eval16Fn(wIn, &wOutOfGamut, p ->GamutCheck ->Data);
    if (wOutOfGamut >= 1) {

        cmsUInt16Number i;

        for (i=0; i < p ->Lut->OutputChannels; i++)
            wOut[i] = Alarm[i];
    }
    else
        p ->Lut ->Eval16Fn(wIn, wOut, p -> Lut->Data);
}

// Gamut check, No caché, 16 bits.
static
void PrecalculatedXFORMGamutCheck(_cmsTRANSFORM* p,
                                  const void* in,
                                  void* out, cmsUInt32Number Size)
{
    cmsUInt8Number* accum;
    cmsUInt8Number* output;
    cmsUInt16Number wIn[cmsMAXCHANNELS], wOut[cmsMAXCHANNELS];
    cmsUInt32Number i, n;

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;
    n = Size;                    // Buffer len

    for (i=0; i < n; i++) {

        accum = p -> FromInput(p, wIn, accum, Size);
        TransformOnePixelWithGamutCheck(p, wIn, wOut);
        output = p -> ToOutput(p, wOut, output, Size);
    }
}


// No gamut check, Caché, 16 bits,
static
void CachedXFORM(_cmsTRANSFORM* p,
                 const void* in,
                 void* out, cmsUInt32Number Size)
{
    cmsUInt8Number* accum;
    cmsUInt8Number* output;
    cmsUInt16Number wIn[cmsMAXCHANNELS], wOut[cmsMAXCHANNELS];
    cmsUInt32Number i, n;
    cmsUInt16Number CacheIn[cmsMAXCHANNELS], CacheOut[cmsMAXCHANNELS];

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;
    n = Size;                    // Buffer len

    // Empty buffers for quick memcmp
    memset(wIn,  0, sizeof(wIn));
    memset(wOut, 0, sizeof(wOut));


    LCMS_READ_LOCK(&p ->rwlock);
    memmove(CacheIn,  p ->CacheIn, sizeof(CacheIn));
    memmove(CacheOut, p ->CacheOut, sizeof(CacheOut));
    LCMS_UNLOCK(&p ->rwlock);

    for (i=0; i < n; i++) {

        accum = p -> FromInput(p, wIn, accum, Size);

        if (memcmp(wIn, CacheIn, sizeof(CacheIn)) == 0) {

            memmove(wOut, CacheOut, sizeof(CacheOut));
        }
        else {

            p ->Lut ->Eval16Fn(wIn, wOut, p -> Lut->Data);

            memmove(CacheIn,  wIn,  sizeof(CacheIn));
            memmove(CacheOut, wOut, sizeof(CacheOut));
        }

        output = p -> ToOutput(p, wOut, output, Size);
    }


    LCMS_WRITE_LOCK(&p ->rwlock);
    memmove(p->CacheIn,  CacheIn, sizeof(CacheIn));
    memmove(p->CacheOut, CacheOut, sizeof(CacheOut));
    LCMS_UNLOCK(&p ->rwlock);
}


// All those nice features together
static
void CachedXFORMGamutCheck(_cmsTRANSFORM* p,
                           const void* in,
                           void* out, cmsUInt32Number Size)
{
       cmsUInt8Number* accum;
       cmsUInt8Number* output;
       cmsUInt16Number wIn[cmsMAXCHANNELS], wOut[cmsMAXCHANNELS];
       cmsUInt32Number i, n;
       cmsUInt16Number CacheIn[cmsMAXCHANNELS], CacheOut[cmsMAXCHANNELS];

       accum  = (cmsUInt8Number*)  in;
       output = (cmsUInt8Number*)  out;
       n = Size;                    // Buffer len

       // Empty buffers for quick memcmp
       memset(wIn,  0, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);
       memset(wOut, 0, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);

       LCMS_READ_LOCK(&p ->rwlock);
           memmove(CacheIn,  p ->CacheIn, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);
           memmove(CacheOut, p ->CacheOut, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);
       LCMS_UNLOCK(&p ->rwlock);


       for (i=0; i < n; i++) {

            accum = p -> FromInput(p, wIn, accum, Size);

            if (memcmp(wIn, CacheIn, sizeof(cmsUInt16Number) * cmsMAXCHANNELS) == 0) {
                    memmove(wOut, CacheOut, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);
            }
            else {
                    TransformOnePixelWithGamutCheck(p, wIn, wOut);
                    memmove(CacheIn, wIn, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);
                    memmove(CacheOut, wOut, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);
            }

            output = p -> ToOutput(p, wOut, output, Size);
       }

        LCMS_WRITE_LOCK(&p ->rwlock);
           memmove(p->CacheIn,  CacheIn, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);
           memmove(p->CacheOut, CacheOut, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);
        LCMS_UNLOCK(&p ->rwlock);
}




// Allocate transform struct and set it to defaults
static
_cmsTRANSFORM* AllocEmptyTransform(cmsContext ContextID, cmsUInt32Number InputFormat, cmsUInt32Number OutputFormat, cmsUInt32Number dwFlags)
{
    // Allocate needed memory
    _cmsTRANSFORM* p = (_cmsTRANSFORM*) _cmsMallocZero(ContextID, sizeof(_cmsTRANSFORM));
    if (!p) return NULL;

    // Check whatever this is a true floating point transform
    if (_cmsFormatterIsFloat(InputFormat) && _cmsFormatterIsFloat(OutputFormat)) {

        // Get formatter function always return a valid union, but the contents of this union may be NULL.
        p ->FromInputFloat = _cmsGetFormatter(InputFormat,  cmsFormatterInput, CMS_PACK_FLAGS_FLOAT).FmtFloat;
        p ->ToOutputFloat  = _cmsGetFormatter(OutputFormat, cmsFormatterOutput, CMS_PACK_FLAGS_FLOAT).FmtFloat;

        if (p ->FromInputFloat == NULL || p ->ToOutputFloat == NULL) {

            cmsSignalError(ContextID, cmsERROR_UNKNOWN_EXTENSION, "Unsupported raster format");
            _cmsFree(ContextID, p);
            return NULL;
        }

        // Float transforms don't use caché, always are non-NULL
        p ->xform = FloatXFORM;
    }
    else {

        p ->FromInput = _cmsGetFormatter(InputFormat,  cmsFormatterInput, CMS_PACK_FLAGS_16BITS).Fmt16;
        p ->ToOutput  = _cmsGetFormatter(OutputFormat, cmsFormatterOutput, CMS_PACK_FLAGS_16BITS).Fmt16;

        if (p ->FromInput == NULL || p ->ToOutput == NULL) {

            cmsSignalError(ContextID, cmsERROR_UNKNOWN_EXTENSION, "Unsupported raster format");
            _cmsFree(ContextID, p);
            return NULL;
        }

        if (dwFlags & cmsFLAGS_NULLTRANSFORM) {

            p ->xform = NullXFORM;
        }
        else {
            if (dwFlags & cmsFLAGS_NOCACHE) {

                if (dwFlags & cmsFLAGS_GAMUTCHECK)
                    p ->xform = PrecalculatedXFORMGamutCheck;  // Gamut check, no caché
                else
                    p ->xform = PrecalculatedXFORM;  // No caché, no gamut check
            }
            else {

                if (dwFlags & cmsFLAGS_GAMUTCHECK)
                    p ->xform = CachedXFORMGamutCheck;    // Gamut check, caché
                else
                    p ->xform = CachedXFORM;  // No gamut check, caché

            }
        }
    }


    // Create a mutex for shared memory
    LCMS_CREATE_LOCK(&p->rwlock);

    p ->InputFormat     = InputFormat;
    p ->OutputFormat    = OutputFormat;
    p ->dwOriginalFlags = dwFlags;
    p ->ContextID       = ContextID;
    return p;
}

static
cmsBool GetXFormColorSpaces(int nProfiles, cmsHPROFILE hProfiles[], cmsColorSpaceSignature* Input, cmsColorSpaceSignature* Output)
{
    cmsColorSpaceSignature ColorSpaceIn, ColorSpaceOut;
    cmsColorSpaceSignature PostColorSpace;
    int i;

    if (hProfiles[0] == NULL) return FALSE;

    *Input = PostColorSpace = cmsGetColorSpace(hProfiles[0]);

    for (i=0; i < nProfiles; i++) {

        cmsHPROFILE hProfile = hProfiles[i];

        int lIsInput = (PostColorSpace != cmsSigXYZData) &&
                       (PostColorSpace != cmsSigLabData);

        if (hProfile == NULL) return FALSE;

        if (lIsInput) {

            ColorSpaceIn    = cmsGetColorSpace(hProfile);
            ColorSpaceOut   = cmsGetPCS(hProfile);
        }
        else {

            ColorSpaceIn    = cmsGetPCS(hProfile);
            ColorSpaceOut   = cmsGetColorSpace(hProfile);
        }

        PostColorSpace = ColorSpaceOut;
    }

    *Output = PostColorSpace;

    return TRUE;
}

// Check colorspace
static
cmsBool  IsProperColorSpace(cmsColorSpaceSignature Check, cmsUInt32Number dwFormat)
{
    int Space1 = T_COLORSPACE(dwFormat);
    int Space2 = _cmsLCMScolorSpace(Check);

    if (Space1 == PT_ANY) return TRUE;
    if (Space1 == Space2) return TRUE;

    if (Space1 == PT_LabV2 && Space2 == PT_Lab) return TRUE;
    if (Space1 == PT_Lab   && Space2 == PT_LabV2) return TRUE;

    return FALSE;
}

// ----------------------------------------------------------------------------------------------------------------

// New to lcms 2.0 -- have all parameters available.
cmsHTRANSFORM CMSEXPORT cmsCreateExtendedTransform(cmsContext ContextID,
                                                   cmsUInt32Number nProfiles, cmsHPROFILE hProfiles[],
                                                   cmsBool  BPC[],
                                                   cmsUInt32Number Intents[],
                                                   cmsFloat64Number AdaptationStates[],
                                                   cmsHPROFILE hGamutProfile,
                                                   cmsUInt32Number nGamutPCSposition,
                                                   cmsUInt32Number InputFormat,
                                                   cmsUInt32Number OutputFormat,
                                                   cmsUInt32Number dwFlags)
{
    _cmsTRANSFORM* xform;
    cmsBool  FloatTransform;
    cmsColorSpaceSignature EntryColorSpace;
    cmsColorSpaceSignature ExitColorSpace;
    cmsPipeline* Lut;
    cmsUInt32Number LastIntent = Intents[nProfiles-1];

    // If gamut check is requested, make sure we have a gamut profile
    if (dwFlags & cmsFLAGS_GAMUTCHECK) {
        if (hGamutProfile == NULL) dwFlags &= ~cmsFLAGS_GAMUTCHECK;
    }

    // On floating point transforms, inhibit optimizations
    FloatTransform = (_cmsFormatterIsFloat(InputFormat) && _cmsFormatterIsFloat(OutputFormat));

    if (_cmsFormatterIsFloat(InputFormat) || _cmsFormatterIsFloat(OutputFormat))
        dwFlags |= cmsFLAGS_NOCACHE;

    // Mark entry/exit spaces
    if (!GetXFormColorSpaces(nProfiles, hProfiles, &EntryColorSpace, &ExitColorSpace)) {
        cmsSignalError(ContextID, cmsERROR_NULL, "NULL input profiles on transform");
        return NULL;
    }

    // Check if proper colorspaces
    if (!IsProperColorSpace(EntryColorSpace, InputFormat)) {
        cmsSignalError(ContextID, cmsERROR_COLORSPACE_CHECK, "Wrong input color space on transform");
        return NULL;
    }

    if (!IsProperColorSpace(ExitColorSpace, OutputFormat)) {
        cmsSignalError(ContextID, cmsERROR_COLORSPACE_CHECK, "Wrong output color space on transform");
        return NULL;
    }

    // Create a pipeline with all transformations
    Lut = _cmsLinkProfiles(ContextID, nProfiles, Intents, hProfiles, BPC, AdaptationStates, dwFlags);
    if (Lut == NULL) {
        cmsSignalError(ContextID, cmsERROR_NOT_SUITABLE, "Couldn't link the profiles");
        return NULL;
    }

    // Optimize the LUT if possible
    _cmsOptimizePipeline(&Lut, LastIntent, &InputFormat, &OutputFormat, &dwFlags);


    // All seems ok
    xform = AllocEmptyTransform(ContextID, InputFormat, OutputFormat, dwFlags);
    if (xform == NULL) {
        cmsPipelineFree(Lut);
        return NULL;
    }

    // Keep values
    xform ->EntryColorSpace = EntryColorSpace;
    xform ->ExitColorSpace  = ExitColorSpace;
    xform ->Lut             = Lut;


    // Create a gamut check LUT if requested
    if (hGamutProfile != NULL && (dwFlags & cmsFLAGS_GAMUTCHECK))
        xform ->GamutCheck  = _cmsCreateGamutCheckPipeline(ContextID, hProfiles,
                                                        BPC, Intents,
                                                        AdaptationStates,
                                                        nGamutPCSposition,
                                                        hGamutProfile);


    // Try to read input and output colorant table
    if (cmsIsTag(hProfiles[0], cmsSigColorantTableTag)) {

        // Input table can only come in this way.
        xform ->InputColorant = cmsDupNamedColorList((cmsNAMEDCOLORLIST*) cmsReadTag(hProfiles[0], cmsSigColorantTableTag));
    }

    // Output is a little bit more complex.
    if (cmsGetDeviceClass(hProfiles[nProfiles-1]) == cmsSigLinkClass) {

        // This tag may exist only on devicelink profiles.
        if (cmsIsTag(hProfiles[nProfiles-1], cmsSigColorantTableOutTag)) {

            // It may be NULL if error
            xform ->OutputColorant = cmsDupNamedColorList((cmsNAMEDCOLORLIST*) cmsReadTag(hProfiles[nProfiles-1], cmsSigColorantTableOutTag));
        }

    } else {

        if (cmsIsTag(hProfiles[nProfiles-1], cmsSigColorantTableTag)) {

            xform -> OutputColorant = cmsDupNamedColorList((cmsNAMEDCOLORLIST*) cmsReadTag(hProfiles[nProfiles-1], cmsSigColorantTableTag));
        }
    }

    // Store the sequence of profiles
    if (dwFlags & cmsFLAGS_KEEP_SEQUENCE) {
        xform ->Sequence = _cmsCompileProfileSequence(ContextID, nProfiles, hProfiles);
    }
    else
        xform ->Sequence = NULL;

    // If this is a cached transform, init first value, which is zero (16 bits only)
    if (!(dwFlags & cmsFLAGS_NOCACHE)) {

        memset(&xform ->CacheIn, 0, sizeof(xform ->CacheIn));

        if (xform ->GamutCheck != NULL) {
            TransformOnePixelWithGamutCheck(xform, xform ->CacheIn, xform->CacheOut);
        }
        else {

            xform ->Lut ->Eval16Fn(xform ->CacheIn, xform->CacheOut, xform -> Lut->Data);
        }

    }

    return (cmsHTRANSFORM) xform;
}

// Multiprofile transforms: Gamut check is not available here, as it is unclear from which profile the gamut comes.

cmsHTRANSFORM CMSEXPORT cmsCreateMultiprofileTransformTHR(cmsContext ContextID,
                                                       cmsHPROFILE hProfiles[],
                                                       cmsUInt32Number nProfiles,
                                                       cmsUInt32Number InputFormat,
                                                       cmsUInt32Number OutputFormat,
                                                       cmsUInt32Number Intent,
                                                       cmsUInt32Number dwFlags)
{
    cmsUInt32Number i;
    cmsBool BPC[256];
    cmsUInt32Number Intents[256];
    cmsFloat64Number AdaptationStates[256];

    if (nProfiles <= 0 || nProfiles > 255) {
         cmsSignalError(ContextID, cmsERROR_RANGE, "Wrong number of profiles. 1..255 expected, %d found.", nProfiles);
        return NULL;
    }

    for (i=0; i < nProfiles; i++) {
        BPC[i] = dwFlags & cmsFLAGS_BLACKPOINTCOMPENSATION ? TRUE : FALSE;
        Intents[i] = Intent;
        AdaptationStates[i] = GlobalAdaptationState;
    }


    return cmsCreateExtendedTransform(ContextID, nProfiles, hProfiles, BPC, Intents, AdaptationStates, NULL, 0, InputFormat, OutputFormat, dwFlags);
}



cmsHTRANSFORM CMSEXPORT cmsCreateMultiprofileTransform(cmsHPROFILE hProfiles[],
                                                  cmsUInt32Number nProfiles,
                                                  cmsUInt32Number InputFormat,
                                                  cmsUInt32Number OutputFormat,
                                                  cmsUInt32Number Intent,
                                                  cmsUInt32Number dwFlags)
{

    if (nProfiles <= 0 || nProfiles > 255) {
         cmsSignalError(NULL, cmsERROR_RANGE, "Wrong number of profiles. 1..255 expected, %d found.", nProfiles);
         return NULL;
    }

    return cmsCreateMultiprofileTransformTHR(cmsGetProfileContextID(hProfiles[0]),
                                                  hProfiles,
                                                  nProfiles,
                                                  InputFormat,
                                                  OutputFormat,
                                                  Intent,
                                                  dwFlags);
}

cmsHTRANSFORM CMSEXPORT cmsCreateTransformTHR(cmsContext ContextID,
                                              cmsHPROFILE Input,
                                              cmsUInt32Number InputFormat,
                                              cmsHPROFILE Output,
                                              cmsUInt32Number OutputFormat,
                                              cmsUInt32Number Intent,
                                              cmsUInt32Number dwFlags)
{

    cmsHPROFILE hArray[2];

    hArray[0] = Input;
    hArray[1] = Output;

    return cmsCreateMultiprofileTransformTHR(ContextID, hArray, Output == NULL ? 1 : 2, InputFormat, OutputFormat, Intent, dwFlags);
}

CMSAPI cmsHTRANSFORM CMSEXPORT cmsCreateTransform(cmsHPROFILE Input,
                                                  cmsUInt32Number InputFormat,
                                                  cmsHPROFILE Output,
                                                  cmsUInt32Number OutputFormat,
                                                  cmsUInt32Number Intent,
                                                  cmsUInt32Number dwFlags)
{
    return cmsCreateTransformTHR(cmsGetProfileContextID(Input), Input, InputFormat, Output, OutputFormat, Intent, dwFlags);
}


cmsHTRANSFORM CMSEXPORT cmsCreateProofingTransformTHR(cmsContext ContextID,
                                                   cmsHPROFILE InputProfile,
                                                   cmsUInt32Number InputFormat,
                                                   cmsHPROFILE OutputProfile,
                                                   cmsUInt32Number OutputFormat,
                                                   cmsHPROFILE ProofingProfile,
                                                   cmsUInt32Number nIntent,
                                                   cmsUInt32Number ProofingIntent,
                                                   cmsUInt32Number dwFlags)
{
    cmsHPROFILE hArray[4];
    cmsUInt32Number Intents[4];
    cmsBool  BPC[4];
    cmsFloat64Number Adaptation[4];
    cmsBool  DoBPC = (dwFlags & cmsFLAGS_BLACKPOINTCOMPENSATION) ? TRUE : FALSE;


    hArray[0]  = InputProfile; hArray[1] = ProofingProfile; hArray[2]  = ProofingProfile;               hArray[3] = OutputProfile;
    Intents[0] = nIntent;      Intents[1] = nIntent;        Intents[2] = INTENT_RELATIVE_COLORIMETRIC;  Intents[3] = ProofingIntent;
    BPC[0]     = DoBPC;        BPC[1] = DoBPC;              BPC[2] = 0;                                 BPC[3] = 0;

    Adaptation[0] = Adaptation[1] = Adaptation[2] = Adaptation[3] = GlobalAdaptationState;

    if (!(dwFlags & (cmsFLAGS_SOFTPROOFING|cmsFLAGS_GAMUTCHECK)))
        return cmsCreateTransformTHR(ContextID, InputProfile, InputFormat, OutputProfile, OutputFormat, nIntent, dwFlags);

    return cmsCreateExtendedTransform(ContextID, 4, hArray, BPC, Intents, Adaptation,
                                        ProofingProfile, 1, InputFormat, OutputFormat, dwFlags);

}


cmsHTRANSFORM CMSEXPORT cmsCreateProofingTransform(cmsHPROFILE InputProfile,
                                                   cmsUInt32Number InputFormat,
                                                   cmsHPROFILE OutputProfile,
                                                   cmsUInt32Number OutputFormat,
                                                   cmsHPROFILE ProofingProfile,
                                                   cmsUInt32Number nIntent,
                                                   cmsUInt32Number ProofingIntent,
                                                   cmsUInt32Number dwFlags)
{
    return cmsCreateProofingTransformTHR(cmsGetProfileContextID(InputProfile),
                                                   InputProfile,
                                                   InputFormat,
                                                   OutputProfile,
                                                   OutputFormat,
                                                   ProofingProfile,
                                                   nIntent,
                                                   ProofingIntent,
                                                   dwFlags);
}


// Grab the ContextID from an open transform. Returns NULL if a NULL transform is passed
cmsContext CMSEXPORT cmsGetTransformContextID(cmsHTRANSFORM hTransform)
{
    _cmsTRANSFORM* xform = (_cmsTRANSFORM*) hTransform;

    if (xform == NULL) return NULL;
    return xform -> ContextID;
}
