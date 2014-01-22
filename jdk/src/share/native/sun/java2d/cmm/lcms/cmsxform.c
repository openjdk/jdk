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
//  Copyright (c) 1998-2011 Marti Maria Saguer
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
static volatile cmsUInt16Number Alarm[cmsMAXCHANNELS] = { 0x7F00, 0x7F00, 0x7F00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
static volatile cmsFloat64Number GlobalAdaptationState = 1;

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

    if (p ->UserData)
        p ->FreeUserData(p ->ContextID, p ->UserData);

    _cmsFree(p ->ContextID, (void *) p);
}

// Apply transform.
void CMSEXPORT cmsDoTransform(cmsHTRANSFORM  Transform,
                              const void* InputBuffer,
                              void* OutputBuffer,
                              cmsUInt32Number Size)

{
    _cmsTRANSFORM* p = (_cmsTRANSFORM*) Transform;

    p -> xform(p, InputBuffer, OutputBuffer, Size, Size);
}


// Apply transform.
void CMSEXPORT cmsDoTransformStride(cmsHTRANSFORM  Transform,
                              const void* InputBuffer,
                              void* OutputBuffer,
                              cmsUInt32Number Size, cmsUInt32Number Stride)

{
    _cmsTRANSFORM* p = (_cmsTRANSFORM*) Transform;

    p -> xform(p, InputBuffer, OutputBuffer, Size, Stride);
}


// Transform routines ----------------------------------------------------------------------------------------------------------

// Float xform converts floats. Since there are no performance issues, one routine does all job, including gamut check.
// Note that because extended range, we can use a -1.0 value for out of gamut in this case.
static
void FloatXFORM(_cmsTRANSFORM* p,
                const void* in,
                void* out, cmsUInt32Number Size, cmsUInt32Number Stride)
{
    cmsUInt8Number* accum;
    cmsUInt8Number* output;
    cmsFloat32Number fIn[cmsMAXCHANNELS], fOut[cmsMAXCHANNELS];
    cmsFloat32Number OutOfGamut;
    cmsUInt32Number i, j;

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;

    for (i=0; i < Size; i++) {

        accum = p -> FromInputFloat(p, fIn, accum, Stride);

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
        output = p -> ToOutputFloat(p, fOut, output, Stride);
    }
}

// 16 bit precision -----------------------------------------------------------------------------------------------------------

// Null transformation, only applies formatters. No caché
static
void NullXFORM(_cmsTRANSFORM* p,
               const void* in,
               void* out, cmsUInt32Number Size,
               cmsUInt32Number Stride)
{
    cmsUInt8Number* accum;
    cmsUInt8Number* output;
    cmsUInt16Number wIn[cmsMAXCHANNELS];
    cmsUInt32Number i, n;

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;
    n = Size;                    // Buffer len

    for (i=0; i < n; i++) {

        accum  = p -> FromInput(p, wIn, accum, Stride);
        output = p -> ToOutput(p, wIn, output, Stride);
    }
}


// No gamut check, no cache, 16 bits
static
void PrecalculatedXFORM(_cmsTRANSFORM* p,
                        const void* in,
                        void* out, cmsUInt32Number Size, cmsUInt32Number Stride)
{
    register cmsUInt8Number* accum;
    register cmsUInt8Number* output;
    cmsUInt16Number wIn[cmsMAXCHANNELS], wOut[cmsMAXCHANNELS];
    cmsUInt32Number i, n;

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;
    n = Size;

    for (i=0; i < n; i++) {

        accum = p -> FromInput(p, wIn, accum, Stride);
        p ->Lut ->Eval16Fn(wIn, wOut, p -> Lut->Data);
        output = p -> ToOutput(p, wOut, output, Stride);
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
                                  void* out, cmsUInt32Number Size, cmsUInt32Number Stride)
{
    cmsUInt8Number* accum;
    cmsUInt8Number* output;
    cmsUInt16Number wIn[cmsMAXCHANNELS], wOut[cmsMAXCHANNELS];
    cmsUInt32Number i, n;

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;
    n = Size;                    // Buffer len

    for (i=0; i < n; i++) {

        accum = p -> FromInput(p, wIn, accum, Stride);
        TransformOnePixelWithGamutCheck(p, wIn, wOut);
        output = p -> ToOutput(p, wOut, output, Stride);
    }
}


// No gamut check, Caché, 16 bits,
static
void CachedXFORM(_cmsTRANSFORM* p,
                 const void* in,
                 void* out, cmsUInt32Number Size, cmsUInt32Number Stride)
{
    cmsUInt8Number* accum;
    cmsUInt8Number* output;
    cmsUInt16Number wIn[cmsMAXCHANNELS], wOut[cmsMAXCHANNELS];
    cmsUInt32Number i, n;
    _cmsCACHE Cache;

    accum  = (cmsUInt8Number*)  in;
    output = (cmsUInt8Number*)  out;
    n = Size;                    // Buffer len

    // Empty buffers for quick memcmp
    memset(wIn,  0, sizeof(wIn));
    memset(wOut, 0, sizeof(wOut));

    // Get copy of zero cache
    memcpy(&Cache, &p ->Cache, sizeof(Cache));

    for (i=0; i < n; i++) {

        accum = p -> FromInput(p, wIn, accum, Stride);

        if (memcmp(wIn, Cache.CacheIn, sizeof(Cache.CacheIn)) == 0) {

            memcpy(wOut, Cache.CacheOut, sizeof(Cache.CacheOut));
        }
        else {

            p ->Lut ->Eval16Fn(wIn, wOut, p -> Lut->Data);

            memcpy(Cache.CacheIn,  wIn,  sizeof(Cache.CacheIn));
            memcpy(Cache.CacheOut, wOut, sizeof(Cache.CacheOut));
        }

        output = p -> ToOutput(p, wOut, output, Stride);
    }

}


// All those nice features together
static
void CachedXFORMGamutCheck(_cmsTRANSFORM* p,
                           const void* in,
                           void* out, cmsUInt32Number Size, cmsUInt32Number Stride)
{
       cmsUInt8Number* accum;
       cmsUInt8Number* output;
       cmsUInt16Number wIn[cmsMAXCHANNELS], wOut[cmsMAXCHANNELS];
       cmsUInt32Number i, n;
       _cmsCACHE Cache;

       accum  = (cmsUInt8Number*)  in;
       output = (cmsUInt8Number*)  out;
       n = Size;                    // Buffer len

       // Empty buffers for quick memcmp
       memset(wIn,  0, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);
       memset(wOut, 0, sizeof(cmsUInt16Number) * cmsMAXCHANNELS);

       // Get copy of zero cache
       memcpy(&Cache, &p ->Cache, sizeof(Cache));

       for (i=0; i < n; i++) {

            accum = p -> FromInput(p, wIn, accum, Stride);

            if (memcmp(wIn, Cache.CacheIn, sizeof(Cache.CacheIn)) == 0) {
                    memcpy(wOut, Cache.CacheOut, sizeof(Cache.CacheOut));
            }
            else {
                    TransformOnePixelWithGamutCheck(p, wIn, wOut);
                    memcpy(Cache.CacheIn, wIn, sizeof(Cache.CacheIn));
                    memcpy(Cache.CacheOut, wOut, sizeof(Cache.CacheOut));
            }

            output = p -> ToOutput(p, wOut, output, Stride);
       }

}

// -------------------------------------------------------------------------------------------------------------

// List of used-defined transform factories
typedef struct _cmsTransformCollection_st {

    _cmsTransformFactory  Factory;
    struct _cmsTransformCollection_st *Next;

} _cmsTransformCollection;

// The linked list head
static _cmsTransformCollection* TransformCollection = NULL;

// Register new ways to transform
cmsBool  _cmsRegisterTransformPlugin(cmsContext id, cmsPluginBase* Data)
{
    cmsPluginTransform* Plugin = (cmsPluginTransform*) Data;
    _cmsTransformCollection* fl;

      if (Data == NULL) {

        // Free the chain. Memory is safely freed at exit
        TransformCollection = NULL;
        return TRUE;
    }

    // Factory callback is required
   if (Plugin ->Factory == NULL) return FALSE;


    fl = (_cmsTransformCollection*) _cmsPluginMalloc(id, sizeof(_cmsTransformCollection));
    if (fl == NULL) return FALSE;

      // Copy the parameters
    fl ->Factory = Plugin ->Factory;

    // Keep linked list
    fl ->Next = TransformCollection;
    TransformCollection = fl;

    // All is ok
    return TRUE;
}


void CMSEXPORT _cmsSetTransformUserData(struct _cmstransform_struct *CMMcargo, void* ptr, _cmsFreeUserDataFn FreePrivateDataFn)
{
    _cmsAssert(CMMcargo != NULL);
    CMMcargo ->UserData = ptr;
    CMMcargo ->FreeUserData = FreePrivateDataFn;
}

// returns the pointer defined by the plug-in to store private data
void * CMSEXPORT _cmsGetTransformUserData(struct _cmstransform_struct *CMMcargo)
{
    _cmsAssert(CMMcargo != NULL);
    return CMMcargo ->UserData;
}

// returns the current formatters
void CMSEXPORT _cmsGetTransformFormatters16(struct _cmstransform_struct *CMMcargo, cmsFormatter16* FromInput, cmsFormatter16* ToOutput)
{
     _cmsAssert(CMMcargo != NULL);
     if (FromInput) *FromInput = CMMcargo ->FromInput;
     if (ToOutput)  *ToOutput  = CMMcargo ->ToOutput;
}

void CMSEXPORT _cmsGetTransformFormattersFloat(struct _cmstransform_struct *CMMcargo, cmsFormatterFloat* FromInput, cmsFormatterFloat* ToOutput)
{
     _cmsAssert(CMMcargo != NULL);
     if (FromInput) *FromInput = CMMcargo ->FromInputFloat;
     if (ToOutput)  *ToOutput  = CMMcargo ->ToOutputFloat;
}


// Allocate transform struct and set it to defaults. Ask the optimization plug-in about if those formats are proper
// for separated transforms. If this is the case,
static
_cmsTRANSFORM* AllocEmptyTransform(cmsContext ContextID, cmsPipeline* lut,
                                               cmsUInt32Number Intent, cmsUInt32Number* InputFormat, cmsUInt32Number* OutputFormat, cmsUInt32Number* dwFlags)
{
     _cmsTransformCollection* Plugin;

    // Allocate needed memory
    _cmsTRANSFORM* p = (_cmsTRANSFORM*) _cmsMallocZero(ContextID, sizeof(_cmsTRANSFORM));
    if (!p) return NULL;

    // Store the proposed pipeline
    p ->Lut = lut;

    // Let's see if any plug-in want to do the transform by itself
    for (Plugin = TransformCollection;
        Plugin != NULL;
        Plugin = Plugin ->Next) {

            if (Plugin ->Factory(&p->xform, &p->UserData, &p ->FreeUserData, &p ->Lut, InputFormat, OutputFormat, dwFlags)) {

                // Last plugin in the declaration order takes control. We just keep
                // the original parameters as a logging.
                // Note that cmsFLAGS_CAN_CHANGE_FORMATTER is not set, so by default
                // an optimized transform is not reusable. The plug-in can, however, change
                // the flags and make it suitable.

                p ->ContextID       = ContextID;
                p ->InputFormat     = *InputFormat;
                p ->OutputFormat    = *OutputFormat;
                p ->dwOriginalFlags = *dwFlags;

                // Fill the formatters just in case the optimized routine is interested.
                // No error is thrown if the formatter doesn't exist. It is up to the optimization
                // factory to decide what to do in those cases.
                p ->FromInput      = _cmsGetFormatter(*InputFormat,  cmsFormatterInput, CMS_PACK_FLAGS_16BITS).Fmt16;
                p ->ToOutput       = _cmsGetFormatter(*OutputFormat, cmsFormatterOutput, CMS_PACK_FLAGS_16BITS).Fmt16;
                p ->FromInputFloat = _cmsGetFormatter(*InputFormat,  cmsFormatterInput, CMS_PACK_FLAGS_FLOAT).FmtFloat;
                p ->ToOutputFloat  = _cmsGetFormatter(*OutputFormat, cmsFormatterOutput, CMS_PACK_FLAGS_FLOAT).FmtFloat;

                return p;
            }
    }

    // Not suitable for the transform plug-in, let's check  the pipeline plug-in
    if (p ->Lut != NULL)
        _cmsOptimizePipeline(&p->Lut, Intent, InputFormat, OutputFormat, dwFlags);

    // Check whatever this is a true floating point transform
    if (_cmsFormatterIsFloat(*InputFormat) && _cmsFormatterIsFloat(*OutputFormat)) {

        // Get formatter function always return a valid union, but the contents of this union may be NULL.
        p ->FromInputFloat = _cmsGetFormatter(*InputFormat,  cmsFormatterInput, CMS_PACK_FLAGS_FLOAT).FmtFloat;
        p ->ToOutputFloat  = _cmsGetFormatter(*OutputFormat, cmsFormatterOutput, CMS_PACK_FLAGS_FLOAT).FmtFloat;
        *dwFlags |= cmsFLAGS_CAN_CHANGE_FORMATTER;

        if (p ->FromInputFloat == NULL || p ->ToOutputFloat == NULL) {

            cmsSignalError(ContextID, cmsERROR_UNKNOWN_EXTENSION, "Unsupported raster format");
            _cmsFree(ContextID, p);
            return NULL;
        }

        // Float transforms don't use caché, always are non-NULL
        p ->xform = FloatXFORM;
    }
    else {

        if (*InputFormat == 0 && *OutputFormat == 0) {
            p ->FromInput = p ->ToOutput = NULL;
            *dwFlags |= cmsFLAGS_CAN_CHANGE_FORMATTER;
        }
        else {

            int BytesPerPixelInput;

            p ->FromInput = _cmsGetFormatter(*InputFormat,  cmsFormatterInput, CMS_PACK_FLAGS_16BITS).Fmt16;
            p ->ToOutput  = _cmsGetFormatter(*OutputFormat, cmsFormatterOutput, CMS_PACK_FLAGS_16BITS).Fmt16;

            if (p ->FromInput == NULL || p ->ToOutput == NULL) {

                cmsSignalError(ContextID, cmsERROR_UNKNOWN_EXTENSION, "Unsupported raster format");
                _cmsFree(ContextID, p);
                return NULL;
            }

            BytesPerPixelInput = T_BYTES(p ->InputFormat);
            if (BytesPerPixelInput == 0 || BytesPerPixelInput >= 2)
                   *dwFlags |= cmsFLAGS_CAN_CHANGE_FORMATTER;

        }

        if (*dwFlags & cmsFLAGS_NULLTRANSFORM) {

            p ->xform = NullXFORM;
        }
        else {
            if (*dwFlags & cmsFLAGS_NOCACHE) {

                if (*dwFlags & cmsFLAGS_GAMUTCHECK)
                    p ->xform = PrecalculatedXFORMGamutCheck;  // Gamut check, no caché
                else
                    p ->xform = PrecalculatedXFORM;  // No caché, no gamut check
            }
            else {

                if (*dwFlags & cmsFLAGS_GAMUTCHECK)
                    p ->xform = CachedXFORMGamutCheck;    // Gamut check, caché
                else
                    p ->xform = CachedXFORM;  // No gamut check, caché

            }
        }
    }

    p ->InputFormat     = *InputFormat;
    p ->OutputFormat    = *OutputFormat;
    p ->dwOriginalFlags = *dwFlags;
    p ->ContextID       = ContextID;
    p ->UserData        = NULL;
    return p;
}

static
cmsBool GetXFormColorSpaces(int nProfiles, cmsHPROFILE hProfiles[], cmsColorSpaceSignature* Input, cmsColorSpaceSignature* Output)
{
    cmsColorSpaceSignature ColorSpaceIn, ColorSpaceOut;
    cmsColorSpaceSignature PostColorSpace;
    int i;

    if (nProfiles <= 0) return FALSE;
    if (hProfiles[0] == NULL) return FALSE;

    *Input = PostColorSpace = cmsGetColorSpace(hProfiles[0]);

    for (i=0; i < nProfiles; i++) {

        cmsProfileClassSignature cls;
        cmsHPROFILE hProfile = hProfiles[i];

        int lIsInput = (PostColorSpace != cmsSigXYZData) &&
                       (PostColorSpace != cmsSigLabData);

        if (hProfile == NULL) return FALSE;

        cls = cmsGetDeviceClass(hProfile);

        if (cls == cmsSigNamedColorClass) {

            ColorSpaceIn    = cmsSig1colorData;
            ColorSpaceOut   = (nProfiles > 1) ? cmsGetPCS(hProfile) : cmsGetColorSpace(hProfile);
        }
        else
        if (lIsInput || (cls == cmsSigLinkClass)) {

            ColorSpaceIn    = cmsGetColorSpace(hProfile);
            ColorSpaceOut   = cmsGetPCS(hProfile);
        }
        else
        {
            ColorSpaceIn    = cmsGetPCS(hProfile);
            ColorSpaceOut   = cmsGetColorSpace(hProfile);
        }

        if (i==0)
            *Input = ColorSpaceIn;

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

static
void SetWhitePoint(cmsCIEXYZ* wtPt, const cmsCIEXYZ* src)
{
    if (src == NULL) {
        wtPt ->X = cmsD50X;
        wtPt ->Y = cmsD50Y;
        wtPt ->Z = cmsD50Z;
    }
    else {
        wtPt ->X = src->X;
        wtPt ->Y = src->Y;
        wtPt ->Z = src->Z;
    }

}

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
    cmsColorSpaceSignature EntryColorSpace;
    cmsColorSpaceSignature ExitColorSpace;
    cmsPipeline* Lut;
    cmsUInt32Number LastIntent = Intents[nProfiles-1];

    // If it is a fake transform
    if (dwFlags & cmsFLAGS_NULLTRANSFORM)
    {
        return AllocEmptyTransform(ContextID, NULL, INTENT_PERCEPTUAL, &InputFormat, &OutputFormat, &dwFlags);
    }

    // If gamut check is requested, make sure we have a gamut profile
    if (dwFlags & cmsFLAGS_GAMUTCHECK) {
        if (hGamutProfile == NULL) dwFlags &= ~cmsFLAGS_GAMUTCHECK;
    }

    // On floating point transforms, inhibit cache
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

    // Check channel count
    if ((cmsChannelsOf(EntryColorSpace) != cmsPipelineInputChannels(Lut)) ||
        (cmsChannelsOf(ExitColorSpace)  != cmsPipelineOutputChannels(Lut))) {
        cmsSignalError(ContextID, cmsERROR_NOT_SUITABLE, "Channel count doesn't match. Profile is corrupted");
        return NULL;
    }


    // All seems ok
    xform = AllocEmptyTransform(ContextID, Lut, LastIntent, &InputFormat, &OutputFormat, &dwFlags);
    if (xform == NULL) {
        return NULL;
    }

    // Keep values
    xform ->EntryColorSpace = EntryColorSpace;
    xform ->ExitColorSpace  = ExitColorSpace;
    xform ->RenderingIntent = Intents[nProfiles-1];

    // Take white points
    SetWhitePoint(&xform->EntryWhitePoint, (cmsCIEXYZ*) cmsReadTag(hProfiles[0], cmsSigMediaWhitePointTag));
    SetWhitePoint(&xform->ExitWhitePoint,  (cmsCIEXYZ*) cmsReadTag(hProfiles[nProfiles-1], cmsSigMediaWhitePointTag));


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

        memset(&xform ->Cache.CacheIn, 0, sizeof(xform ->Cache.CacheIn));

        if (xform ->GamutCheck != NULL) {
            TransformOnePixelWithGamutCheck(xform, xform ->Cache.CacheIn, xform->Cache.CacheOut);
        }
        else {

            xform ->Lut ->Eval16Fn(xform ->Cache.CacheIn, xform->Cache.CacheOut, xform -> Lut->Data);
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

// Grab the input/output formats
cmsUInt32Number CMSEXPORT cmsGetTransformInputFormat(cmsHTRANSFORM hTransform)
{
    _cmsTRANSFORM* xform = (_cmsTRANSFORM*) hTransform;

    if (xform == NULL) return 0;
    return xform->InputFormat;
}

cmsUInt32Number CMSEXPORT cmsGetTransformOutputFormat(cmsHTRANSFORM hTransform)
{
    _cmsTRANSFORM* xform = (_cmsTRANSFORM*) hTransform;

    if (xform == NULL) return 0;
    return xform->OutputFormat;
}

// For backwards compatibility
cmsBool CMSEXPORT cmsChangeBuffersFormat(cmsHTRANSFORM hTransform,
                                         cmsUInt32Number InputFormat,
                                         cmsUInt32Number OutputFormat)
{

    _cmsTRANSFORM* xform = (_cmsTRANSFORM*) hTransform;
    cmsFormatter16 FromInput, ToOutput;


    // We only can afford to change formatters if previous transform is at least 16 bits
    if (!(xform ->dwOriginalFlags & cmsFLAGS_CAN_CHANGE_FORMATTER)) {

        cmsSignalError(xform ->ContextID, cmsERROR_NOT_SUITABLE, "cmsChangeBuffersFormat works only on transforms created originally with at least 16 bits of precision");
        return FALSE;
    }

    FromInput = _cmsGetFormatter(InputFormat,  cmsFormatterInput, CMS_PACK_FLAGS_16BITS).Fmt16;
    ToOutput  = _cmsGetFormatter(OutputFormat, cmsFormatterOutput, CMS_PACK_FLAGS_16BITS).Fmt16;

    if (FromInput == NULL || ToOutput == NULL) {

        cmsSignalError(xform -> ContextID, cmsERROR_UNKNOWN_EXTENSION, "Unsupported raster format");
        return FALSE;
    }

    xform ->InputFormat  = InputFormat;
    xform ->OutputFormat = OutputFormat;
    xform ->FromInput    = FromInput;
    xform ->ToOutput     = ToOutput;
    return TRUE;
}
