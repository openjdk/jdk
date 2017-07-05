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

#ifndef _lcms_internal_H

// Include plug-in foundation
#ifndef _lcms_plugin_H
#   include "lcms2_plugin.h"
#endif

// ctype is part of C99 as per 7.1.2
#include <ctype.h>

// assert macro is part of C99 as per 7.2
#include <assert.h>

// Some needed constants
#ifndef M_PI
#       define M_PI        3.14159265358979323846
#endif

#ifndef M_LOG10E
#       define M_LOG10E    0.434294481903251827651
#endif

// BorlandC 5.5, VC2003 are broken on that
#if defined(__BORLANDC__) || (_MSC_VER < 1400) // 1400 == VC++ 8.0
#define sinf(x) (float)sin((float)x)
#define sqrtf(x) (float)sqrt((float)x)
#endif


// Alignment of ICC file format uses 4 bytes (cmsUInt32Number)
#define _cmsALIGNLONG(x) (((x)+(sizeof(cmsUInt32Number)-1)) & ~(sizeof(cmsUInt32Number)-1))

// Alignment to memory pointer
#define _cmsALIGNMEM(x)  (((x)+(sizeof(void *) - 1)) & ~(sizeof(void *) - 1))

// Maximum encodeable values in floating point
#define MAX_ENCODEABLE_XYZ  (1.0 + 32767.0/32768.0)
#define MIN_ENCODEABLE_ab2  (-128.0)
#define MAX_ENCODEABLE_ab2  ((65535.0/256.0) - 128.0)
#define MIN_ENCODEABLE_ab4  (-128.0)
#define MAX_ENCODEABLE_ab4  (127.0)

// Maximum of channels for internal pipeline evaluation
#define MAX_STAGE_CHANNELS  128

// Unused parameter warning supression
#define cmsUNUSED_PARAMETER(x) ((void)x)

// The specification for "inline" is section 6.7.4 of the C99 standard (ISO/IEC 9899:1999).
// unfortunately VisualC++ does not conform that
#if defined(_MSC_VER) || defined(__BORLANDC__)
#   define cmsINLINE __inline
#else
#   define cmsINLINE static inline
#endif

// Other replacement functions
#ifdef _MSC_VER
# ifndef snprintf
#       define snprintf  _snprintf
# endif
# ifndef vsnprintf
#       define vsnprintf  _vsnprintf
# endif
#endif


// A fast way to convert from/to 16 <-> 8 bits
#define FROM_8_TO_16(rgb) (cmsUInt16Number) ((((cmsUInt16Number) (rgb)) << 8)|(rgb))
#define FROM_16_TO_8(rgb) (cmsUInt8Number) ((((rgb) * 65281 + 8388608) >> 24) & 0xFF)

// Code analysis is broken on asserts
#ifdef _MSC_VER
#    if (_MSC_VER >= 1500)
#            define _cmsAssert(a)  { assert((a)); __analysis_assume((a)); }
#     else
#            define _cmsAssert(a)   assert((a))
#     endif
#else
#      define _cmsAssert(a)   assert((a))
#endif

//---------------------------------------------------------------------------------

// Determinant lower than that are assumed zero (used on matrix invert)
#define MATRIX_DET_TOLERANCE    0.0001

//---------------------------------------------------------------------------------

// Fixed point
#define FIXED_TO_INT(x)         ((x)>>16)
#define FIXED_REST_TO_INT(x)    ((x)&0xFFFFU)
#define ROUND_FIXED_TO_INT(x)   (((x)+0x8000)>>16)

cmsINLINE cmsS15Fixed16Number _cmsToFixedDomain(int a)                   { return a + ((a + 0x7fff) / 0xffff); }
cmsINLINE int                 _cmsFromFixedDomain(cmsS15Fixed16Number a) { return a - ((a + 0x7fff) >> 16); }

// -----------------------------------------------------------------------------------------------------------

// Fast floor conversion logic. Thanks to Sree Kotay and Stuart Nixon
// note than this only works in the range ..-32767...+32767 because
// mantissa is interpreted as 15.16 fixed point.
// The union is to avoid pointer aliasing overoptimization.
cmsINLINE int _cmsQuickFloor(cmsFloat64Number val)
{
#ifdef CMS_DONT_USE_FAST_FLOOR
    return (int) floor(val);
#else
    const cmsFloat64Number _lcms_double2fixmagic = 68719476736.0 * 1.5;  // 2^36 * 1.5, (52-16=36) uses limited precision to floor
    union {
        cmsFloat64Number val;
        int halves[2];
    } temp;

    temp.val = val + _lcms_double2fixmagic;

#ifdef CMS_USE_BIG_ENDIAN
    return temp.halves[1] >> 16;
#else
    return temp.halves[0] >> 16;
#endif
#endif
}

// Fast floor restricted to 0..65535.0
cmsINLINE cmsUInt16Number _cmsQuickFloorWord(cmsFloat64Number d)
{
    return (cmsUInt16Number) _cmsQuickFloor(d - 32767.0) + 32767U;
}

// Floor to word, taking care of saturation
cmsINLINE cmsUInt16Number _cmsQuickSaturateWord(cmsFloat64Number d)
{
    d += 0.5;
    if (d <= 0) return 0;
    if (d >= 65535.0) return 0xffff;

    return _cmsQuickFloorWord(d);
}

// Plug-In registering ---------------------------------------------------------------

// Specialized function for plug-in memory management. No pairing free() since whole pool is freed at once.
void* _cmsPluginMalloc(cmsUInt32Number size);

// Memory management
cmsBool   _cmsRegisterMemHandlerPlugin(cmsPluginBase* Plugin);

// Interpolation
cmsBool  _cmsRegisterInterpPlugin(cmsPluginBase* Plugin);

// Parametric curves
cmsBool  _cmsRegisterParametricCurvesPlugin(cmsPluginBase* Plugin);

// Formatters management
cmsBool  _cmsRegisterFormattersPlugin(cmsPluginBase* Plugin);

// Tag type management
cmsBool  _cmsRegisterTagTypePlugin(cmsPluginBase* Plugin);

// Tag management
cmsBool  _cmsRegisterTagPlugin(cmsPluginBase* Plugin);

// Intent management
cmsBool  _cmsRegisterRenderingIntentPlugin(cmsPluginBase* Plugin);

// Multi Process elements
cmsBool  _cmsRegisterMultiProcessElementPlugin(cmsPluginBase* Plugin);

// Optimization
cmsBool  _cmsRegisterOptimizationPlugin(cmsPluginBase* Plugin);

// Transform
cmsBool  _cmsRegisterTransformPlugin(cmsPluginBase* Plugin);

// ---------------------------------------------------------------------------------------------------------

// Suballocators. Those are blocks of memory that is freed at the end on whole block.
typedef struct _cmsSubAllocator_chunk_st {

    cmsUInt8Number* Block;
    cmsUInt32Number BlockSize;
    cmsUInt32Number Used;

    struct _cmsSubAllocator_chunk_st* next;

} _cmsSubAllocator_chunk;


typedef struct {

    cmsContext ContextID;
    _cmsSubAllocator_chunk* h;

} _cmsSubAllocator;


_cmsSubAllocator* _cmsCreateSubAlloc(cmsContext ContextID, cmsUInt32Number Initial);
void              _cmsSubAllocDestroy(_cmsSubAllocator* s);
void*             _cmsSubAlloc(_cmsSubAllocator* s, cmsUInt32Number size);

// ----------------------------------------------------------------------------------

// MLU internal representation
typedef struct {

    cmsUInt16Number Language;
    cmsUInt16Number Country;

    cmsUInt32Number StrW;       // Offset to current unicode string
    cmsUInt32Number Len;        // Lenght in bytes

} _cmsMLUentry;

struct _cms_MLU_struct {

    cmsContext ContextID;

    // The directory
    int AllocatedEntries;
    int UsedEntries;
    _cmsMLUentry* Entries;     // Array of pointers to strings allocated in MemPool

    // The Pool
    cmsUInt32Number PoolSize;  // The maximum allocated size
    cmsUInt32Number PoolUsed;  // The used size
    void*  MemPool;            // Pointer to begin of memory pool
};

// Named color list internal representation
typedef struct {

    char Name[cmsMAX_PATH];
    cmsUInt16Number PCS[3];
    cmsUInt16Number DeviceColorant[cmsMAXCHANNELS];

} _cmsNAMEDCOLOR;

struct _cms_NAMEDCOLORLIST_struct {

    cmsUInt32Number nColors;
    cmsUInt32Number Allocated;
    cmsUInt32Number ColorantCount;

    char Prefix[33];      // Prefix and suffix are defined to be 32 characters at most
    char Suffix[33];

    _cmsNAMEDCOLOR* List;

    cmsContext ContextID;
};


// ----------------------------------------------------------------------------------

// This is the internal struct holding profile details.

// Maximum supported tags in a profile
#define MAX_TABLE_TAG       100

typedef struct _cms_iccprofile_struct {

    // I/O handler
    cmsIOHANDLER*            IOhandler;

    // The thread ID
    cmsContext               ContextID;

    // Creation time
    struct tm                Created;

    // Only most important items found in ICC profiles
    cmsUInt32Number          Version;
    cmsProfileClassSignature DeviceClass;
    cmsColorSpaceSignature   ColorSpace;
    cmsColorSpaceSignature   PCS;
    cmsUInt32Number          RenderingIntent;
    cmsUInt32Number          flags;
    cmsUInt32Number          manufacturer, model;
    cmsUInt64Number          attributes;

    cmsProfileID             ProfileID;

    // Dictionary
    cmsUInt32Number          TagCount;
    cmsTagSignature          TagNames[MAX_TABLE_TAG];
    cmsTagSignature          TagLinked[MAX_TABLE_TAG];           // The tag to wich is linked (0=none)
    cmsUInt32Number          TagSizes[MAX_TABLE_TAG];            // Size on disk
    cmsUInt32Number          TagOffsets[MAX_TABLE_TAG];
    cmsBool                  TagSaveAsRaw[MAX_TABLE_TAG];        // True to write uncooked
    void *                   TagPtrs[MAX_TABLE_TAG];
    cmsTagTypeHandler*       TagTypeHandlers[MAX_TABLE_TAG];     // Same structure may be serialized on different types
                                                                 // depending on profile version, so we keep track of the                                                             // type handler for each tag in the list.
    // Special
    cmsBool                  IsWrite;

} _cmsICCPROFILE;

// IO helpers for profiles
cmsBool              _cmsReadHeader(_cmsICCPROFILE* Icc);
cmsBool              _cmsWriteHeader(_cmsICCPROFILE* Icc, cmsUInt32Number UsedSpace);
int                  _cmsSearchTag(_cmsICCPROFILE* Icc, cmsTagSignature sig, cmsBool lFollowLinks);

// Tag types
cmsTagTypeHandler*   _cmsGetTagTypeHandler(cmsTagTypeSignature sig);
cmsTagTypeSignature  _cmsGetTagTrueType(cmsHPROFILE hProfile, cmsTagSignature sig);
cmsTagDescriptor*    _cmsGetTagDescriptor(cmsTagSignature sig);

// Error logging ---------------------------------------------------------------------------------------------------------

void                 _cmsTagSignature2String(char String[5], cmsTagSignature sig);

// Interpolation ---------------------------------------------------------------------------------------------------------

cmsInterpParams*     _cmsComputeInterpParams(cmsContext ContextID, int nSamples, int InputChan, int OutputChan, const void* Table, cmsUInt32Number dwFlags);
cmsInterpParams*     _cmsComputeInterpParamsEx(cmsContext ContextID, const cmsUInt32Number nSamples[], int InputChan, int OutputChan, const void* Table, cmsUInt32Number dwFlags);
void                 _cmsFreeInterpParams(cmsInterpParams* p);
cmsBool              _cmsSetInterpolationRoutine(cmsInterpParams* p);

// Curves ----------------------------------------------------------------------------------------------------------------

// This struct holds information about a segment, plus a pointer to the function that implements the evaluation.
// In the case of table-based, Eval pointer is set to NULL

// The gamma function main structure
struct _cms_curve_struct {

    cmsInterpParams*  InterpParams;  // Private optimizations for interpolation

    cmsUInt32Number   nSegments;     // Number of segments in the curve. Zero for a 16-bit based tables
    cmsCurveSegment*  Segments;      // The segments
    cmsInterpParams** SegInterp;     // Array of private optimizations for interpolation in table-based segments

    cmsParametricCurveEvaluator* Evals;  // Evaluators (one per segment)

    // 16 bit Table-based representation follows
    cmsUInt32Number    nEntries;      // Number of table elements
    cmsUInt16Number*   Table16;       // The table itself.
};


//  Pipelines & Stages ---------------------------------------------------------------------------------------------

// A single stage
struct _cmsStage_struct {

    cmsContext          ContextID;

    cmsStageSignature   Type;           // Identifies the stage
    cmsStageSignature   Implements;     // Identifies the *function* of the stage (for optimizations)

    cmsUInt32Number     InputChannels;  // Input channels -- for optimization purposes
    cmsUInt32Number     OutputChannels; // Output channels -- for optimization purposes

    _cmsStageEvalFn     EvalPtr;        // Points to fn that evaluates the stage (always in floating point)
    _cmsStageDupElemFn  DupElemPtr;     // Points to a fn that duplicates the *data* of the stage
    _cmsStageFreeElemFn FreePtr;        // Points to a fn that sets the *data* of the stage free

    // A generic pointer to whatever memory needed by the stage
    void*               Data;

    // Maintains linked list (used internally)
    struct _cmsStage_struct* Next;
};


// Special Stages (cannot be saved)
cmsStage*        _cmsStageAllocLab2XYZ(cmsContext ContextID);
cmsStage*        _cmsStageAllocXYZ2Lab(cmsContext ContextID);
cmsStage*        _cmsStageAllocLabPrelin(cmsContext ContextID);
cmsStage*        _cmsStageAllocLabV2ToV4(cmsContext ContextID);
cmsStage*        _cmsStageAllocLabV2ToV4curves(cmsContext ContextID);
cmsStage*        _cmsStageAllocLabV4ToV2(cmsContext ContextID);
cmsStage*        _cmsStageAllocNamedColor(cmsNAMEDCOLORLIST* NamedColorList, cmsBool UsePCS);
cmsStage*        _cmsStageAllocIdentityCurves(cmsContext ContextID, int nChannels);
cmsStage*        _cmsStageAllocIdentityCLut(cmsContext ContextID, int nChan);
cmsStage*        _cmsStageNormalizeFromLabFloat(cmsContext ContextID);
cmsStage*        _cmsStageNormalizeFromXyzFloat(cmsContext ContextID);
cmsStage*        _cmsStageNormalizeToLabFloat(cmsContext ContextID);
cmsStage*        _cmsStageNormalizeToXyzFloat(cmsContext ContextID);

// For curve set only
cmsToneCurve**     _cmsStageGetPtrToCurveSet(const cmsStage* mpe);


// Pipeline Evaluator (in floating point)
typedef void (* _cmsPipelineEvalFloatFn)(const cmsFloat32Number In[],
                                         cmsFloat32Number Out[],
                                         const void* Data);

struct _cmsPipeline_struct {

    cmsStage* Elements;                                // Points to elements chain
    cmsUInt32Number InputChannels, OutputChannels;

    // Data & evaluators
    void *Data;

   _cmsOPTeval16Fn         Eval16Fn;
   _cmsPipelineEvalFloatFn EvalFloatFn;
   _cmsFreeUserDataFn      FreeDataFn;
   _cmsDupUserDataFn       DupDataFn;

    cmsContext ContextID;            // Environment

    cmsBool  SaveAs8Bits;            // Implementation-specific: save as 8 bits if possible
};

// LUT reading & creation -------------------------------------------------------------------------------------------

// Read tags using low-level function, provide necessary glue code to adapt versions, etc. All those return a brand new copy
// of the LUTS, since ownership of original is up to the profile. The user should free allocated resources.

cmsPipeline*      _cmsReadInputLUT(cmsHPROFILE hProfile, int Intent);
cmsPipeline*      _cmsReadOutputLUT(cmsHPROFILE hProfile, int Intent);
cmsPipeline*      _cmsReadDevicelinkLUT(cmsHPROFILE hProfile, int Intent);

// Special values
cmsBool           _cmsReadMediaWhitePoint(cmsCIEXYZ* Dest, cmsHPROFILE hProfile);
cmsBool           _cmsReadCHAD(cmsMAT3* Dest, cmsHPROFILE hProfile);

// Profile linker --------------------------------------------------------------------------------------------------

cmsPipeline* _cmsLinkProfiles(cmsContext         ContextID,
                              cmsUInt32Number    nProfiles,
                              cmsUInt32Number    TheIntents[],
                              cmsHPROFILE        hProfiles[],
                              cmsBool            BPC[],
                              cmsFloat64Number   AdaptationStates[],
                              cmsUInt32Number    dwFlags);

// Sequence --------------------------------------------------------------------------------------------------------

cmsSEQ* _cmsReadProfileSequence(cmsHPROFILE hProfile);
cmsBool _cmsWriteProfileSequence(cmsHPROFILE hProfile, const cmsSEQ* seq);
cmsSEQ* _cmsCompileProfileSequence(cmsContext ContextID, cmsUInt32Number nProfiles, cmsHPROFILE hProfiles[]);


// LUT optimization ------------------------------------------------------------------------------------------------

cmsUInt16Number  _cmsQuantizeVal(cmsFloat64Number i, int MaxSamples);
int              _cmsReasonableGridpointsByColorspace(cmsColorSpaceSignature Colorspace, cmsUInt32Number dwFlags);

cmsBool          _cmsEndPointsBySpace(cmsColorSpaceSignature Space,
                                      cmsUInt16Number **White,
                                      cmsUInt16Number **Black,
                                      cmsUInt32Number *nOutputs);

cmsBool          _cmsOptimizePipeline(cmsPipeline**    Lut,
                                      int              Intent,
                                      cmsUInt32Number* InputFormat,
                                      cmsUInt32Number* OutputFormat,
                                      cmsUInt32Number* dwFlags );


// Hi level LUT building ----------------------------------------------------------------------------------------------

cmsPipeline*     _cmsCreateGamutCheckPipeline(cmsContext ContextID,
                                              cmsHPROFILE hProfiles[],
                                              cmsBool  BPC[],
                                              cmsUInt32Number Intents[],
                                              cmsFloat64Number AdaptationStates[],
                                              cmsUInt32Number nGamutPCSposition,
                                              cmsHPROFILE hGamut);


// Formatters ------------------------------------------------------------------------------------------------------------

#define cmsFLAGS_CAN_CHANGE_FORMATTER     0x02000000   // Allow change buffer format

cmsBool         _cmsFormatterIsFloat(cmsUInt32Number Type);
cmsBool         _cmsFormatterIs8bit(cmsUInt32Number Type);

cmsFormatter    _cmsGetFormatter(cmsUInt32Number Type,          // Specific type, i.e. TYPE_RGB_8
                                 cmsFormatterDirection Dir,
                                 cmsUInt32Number dwFlags);


#ifndef CMS_NO_HALF_SUPPORT

// Half float
cmsFloat32Number _cmsHalf2Float(cmsUInt16Number h);
cmsUInt16Number  _cmsFloat2Half(cmsFloat32Number flt);

#endif

// Transform logic ------------------------------------------------------------------------------------------------------

struct _cmstransform_struct;

typedef struct {

    // 1-pixel cache (16 bits only)
    cmsUInt16Number CacheIn[cmsMAXCHANNELS];
    cmsUInt16Number CacheOut[cmsMAXCHANNELS];

} _cmsCACHE;



// Transformation
typedef struct _cmstransform_struct {

    cmsUInt32Number InputFormat, OutputFormat; // Keep formats for further reference

    // Points to transform code
    _cmsTransformFn xform;

    // Formatters, cannot be embedded into LUT because cache
    cmsFormatter16 FromInput;
    cmsFormatter16 ToOutput;

    cmsFormatterFloat FromInputFloat;
    cmsFormatterFloat ToOutputFloat;

    // 1-pixel cache seed for zero as input (16 bits, read only)
    _cmsCACHE Cache;

    // A Pipeline holding the full (optimized) transform
    cmsPipeline* Lut;

    // A Pipeline holding the gamut check. It goes from the input space to bilevel
    cmsPipeline* GamutCheck;

    // Colorant tables
    cmsNAMEDCOLORLIST* InputColorant;       // Input Colorant table
    cmsNAMEDCOLORLIST* OutputColorant;      // Colorant table (for n chans > CMYK)

    // Informational only
    cmsColorSpaceSignature EntryColorSpace;
    cmsColorSpaceSignature ExitColorSpace;

    // Profiles used to create the transform
    cmsSEQ* Sequence;

    cmsUInt32Number  dwOriginalFlags;
    cmsFloat64Number AdaptationState;

    // The intent of this transform. That is usually the last intent in the profilechain, but may differ
    cmsUInt32Number RenderingIntent;

    // An id that uniquely identifies the running context. May be null.
    cmsContext ContextID;

    // A user-defined pointer that can be used to store data for transform plug-ins
    void* UserData;
    _cmsFreeUserDataFn FreeUserData;

} _cmsTRANSFORM;

// --------------------------------------------------------------------------------------------------

cmsHTRANSFORM _cmsChain2Lab(cmsContext             ContextID,
                            cmsUInt32Number        nProfiles,
                            cmsUInt32Number        InputFormat,
                            cmsUInt32Number        OutputFormat,
                            const cmsUInt32Number  Intents[],
                            const cmsHPROFILE      hProfiles[],
                            const cmsBool          BPC[],
                            const cmsFloat64Number AdaptationStates[],
                            cmsUInt32Number        dwFlags);


cmsToneCurve* _cmsBuildKToneCurve(cmsContext       ContextID,
                            cmsUInt32Number        nPoints,
                            cmsUInt32Number        nProfiles,
                            const cmsUInt32Number  Intents[],
                            const cmsHPROFILE      hProfiles[],
                            const cmsBool          BPC[],
                            const cmsFloat64Number AdaptationStates[],
                            cmsUInt32Number        dwFlags);

cmsBool   _cmsAdaptationMatrix(cmsMAT3* r, const cmsMAT3* ConeMatrix, const cmsCIEXYZ* FromIll, const cmsCIEXYZ* ToIll);

cmsBool   _cmsBuildRGB2XYZtransferMatrix(cmsMAT3* r, const cmsCIExyY* WhitePoint, const cmsCIExyYTRIPLE* Primaries);


#define _lcms_internal_H
#endif
