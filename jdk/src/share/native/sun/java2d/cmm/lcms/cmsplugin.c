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


// ----------------------------------------------------------------------------------
// Encoding & Decoding support functions
// ----------------------------------------------------------------------------------

//      Little-Endian to Big-Endian

// Adjust a word value after being readed/ before being written from/to an ICC profile
cmsUInt16Number CMSEXPORT  _cmsAdjustEndianess16(cmsUInt16Number Word)
{
#ifndef CMS_USE_BIG_ENDIAN

    cmsUInt8Number* pByte = (cmsUInt8Number*) &Word;
    cmsUInt8Number tmp;

    tmp = pByte[0];
    pByte[0] = pByte[1];
    pByte[1] = tmp;
#endif

    return Word;
}


// Transports to properly encoded values - note that icc profiles does use big endian notation.

// 1 2 3 4
// 4 3 2 1

cmsUInt32Number CMSEXPORT  _cmsAdjustEndianess32(cmsUInt32Number DWord)
{
#ifndef CMS_USE_BIG_ENDIAN

    cmsUInt8Number* pByte = (cmsUInt8Number*) &DWord;
    cmsUInt8Number temp1;
    cmsUInt8Number temp2;

    temp1 = *pByte++;
    temp2 = *pByte++;
    *(pByte-1) = *pByte;
    *pByte++ = temp2;
    *(pByte-3) = *pByte;
    *pByte = temp1;
#endif
    return DWord;
}

// 1 2 3 4 5 6 7 8
// 8 7 6 5 4 3 2 1

void CMSEXPORT  _cmsAdjustEndianess64(cmsUInt64Number* Result, cmsUInt64Number* QWord)
{

#ifndef CMS_USE_BIG_ENDIAN

    cmsUInt8Number* pIn  = (cmsUInt8Number*) QWord;
    cmsUInt8Number* pOut = (cmsUInt8Number*) Result;

    _cmsAssert(Result != NULL);

    pOut[7] = pIn[0];
    pOut[6] = pIn[1];
    pOut[5] = pIn[2];
    pOut[4] = pIn[3];
    pOut[3] = pIn[4];
    pOut[2] = pIn[5];
    pOut[1] = pIn[6];
    pOut[0] = pIn[7];

#else

    _cmsAssert(Result != NULL);

    *Result = *QWord;
#endif
}

// Auxiliar -- read 8, 16 and 32-bit numbers
cmsBool CMSEXPORT  _cmsReadUInt8Number(cmsIOHANDLER* io, cmsUInt8Number* n)
{
    cmsUInt8Number tmp;

    _cmsAssert(io != NULL);

    if (io -> Read(io, &tmp, sizeof(cmsUInt8Number), 1) != 1)
            return FALSE;

    if (n != NULL) *n = tmp;
    return TRUE;
}

cmsBool CMSEXPORT  _cmsReadUInt16Number(cmsIOHANDLER* io, cmsUInt16Number* n)
{
    cmsUInt16Number tmp;

    _cmsAssert(io != NULL);

    if (io -> Read(io, &tmp, sizeof(cmsUInt16Number), 1) != 1)
            return FALSE;

    if (n != NULL) *n = _cmsAdjustEndianess16(tmp);
    return TRUE;
}

cmsBool CMSEXPORT  _cmsReadUInt16Array(cmsIOHANDLER* io, cmsUInt32Number n, cmsUInt16Number* Array)
{
    cmsUInt32Number i;

    _cmsAssert(io != NULL);

    for (i=0; i < n; i++) {

        if (Array != NULL) {
            if (!_cmsReadUInt16Number(io, Array + i)) return FALSE;
        }
        else {
            if (!_cmsReadUInt16Number(io, NULL)) return FALSE;
        }

    }
    return TRUE;
}

cmsBool CMSEXPORT  _cmsReadUInt32Number(cmsIOHANDLER* io, cmsUInt32Number* n)
{
    cmsUInt32Number tmp;

    _cmsAssert(io != NULL);

    if (io -> Read(io, &tmp, sizeof(cmsUInt32Number), 1) != 1)
            return FALSE;

    if (n != NULL) *n = _cmsAdjustEndianess32(tmp);
    return TRUE;
}

cmsBool CMSEXPORT  _cmsReadFloat32Number(cmsIOHANDLER* io, cmsFloat32Number* n)
{
    cmsUInt32Number tmp;

    _cmsAssert(io != NULL);

    if (io -> Read(io, &tmp, sizeof(cmsFloat32Number), 1) != 1)
            return FALSE;

    if (n != NULL) {

        tmp = _cmsAdjustEndianess32(tmp);
        *n = *(cmsFloat32Number*) &tmp;
    }
    return TRUE;
}


cmsBool CMSEXPORT   _cmsReadUInt64Number(cmsIOHANDLER* io, cmsUInt64Number* n)
{
    cmsUInt64Number tmp;

    _cmsAssert(io != NULL);

    if (io -> Read(io, &tmp, sizeof(cmsUInt64Number), 1) != 1)
            return FALSE;

    if (n != NULL) _cmsAdjustEndianess64(n, &tmp);
    return TRUE;
}


cmsBool CMSEXPORT  _cmsRead15Fixed16Number(cmsIOHANDLER* io, cmsFloat64Number* n)
{
    cmsUInt32Number tmp;

    _cmsAssert(io != NULL);

    if (io -> Read(io, &tmp, sizeof(cmsUInt32Number), 1) != 1)
            return FALSE;

    if (n != NULL) {
        *n = _cms15Fixed16toDouble(_cmsAdjustEndianess32(tmp));
    }

    return TRUE;
}


// Jun-21-2000: Some profiles (those that comes with W2K) comes
// with the media white (media black?) x 100. Add a sanity check

static
void NormalizeXYZ(cmsCIEXYZ* Dest)
{
    while (Dest -> X > 2. &&
           Dest -> Y > 2. &&
           Dest -> Z > 2.) {

               Dest -> X /= 10.;
               Dest -> Y /= 10.;
               Dest -> Z /= 10.;
       }
}

cmsBool CMSEXPORT  _cmsReadXYZNumber(cmsIOHANDLER* io, cmsCIEXYZ* XYZ)
{
    cmsEncodedXYZNumber xyz;

    _cmsAssert(io != NULL);

    if (io ->Read(io, &xyz, sizeof(cmsEncodedXYZNumber), 1) != 1) return FALSE;

    if (XYZ != NULL) {

        XYZ->X = _cms15Fixed16toDouble(_cmsAdjustEndianess32(xyz.X));
        XYZ->Y = _cms15Fixed16toDouble(_cmsAdjustEndianess32(xyz.Y));
        XYZ->Z = _cms15Fixed16toDouble(_cmsAdjustEndianess32(xyz.Z));

        NormalizeXYZ(XYZ);
    }
    return TRUE;
}

cmsBool CMSEXPORT  _cmsWriteUInt8Number(cmsIOHANDLER* io, cmsUInt8Number n)
{
    _cmsAssert(io != NULL);

    if (io -> Write(io, sizeof(cmsUInt8Number), &n) != 1)
            return FALSE;

    return TRUE;
}

cmsBool CMSEXPORT  _cmsWriteUInt16Number(cmsIOHANDLER* io, cmsUInt16Number n)
{
    cmsUInt16Number tmp;

    _cmsAssert(io != NULL);

    tmp = _cmsAdjustEndianess16(n);
    if (io -> Write(io, sizeof(cmsUInt16Number), &tmp) != 1)
            return FALSE;

    return TRUE;
}

cmsBool CMSEXPORT  _cmsWriteUInt16Array(cmsIOHANDLER* io, cmsUInt32Number n, const cmsUInt16Number* Array)
{
    cmsUInt32Number i;

    _cmsAssert(io != NULL);
    _cmsAssert(Array != NULL);

    for (i=0; i < n; i++) {
        if (!_cmsWriteUInt16Number(io, Array[i])) return FALSE;
    }

    return TRUE;
}

cmsBool CMSEXPORT  _cmsWriteUInt32Number(cmsIOHANDLER* io, cmsUInt32Number n)
{
    cmsUInt32Number tmp;

    _cmsAssert(io != NULL);

    tmp = _cmsAdjustEndianess32(n);
    if (io -> Write(io, sizeof(cmsUInt32Number), &tmp) != 1)
            return FALSE;

    return TRUE;
}


cmsBool CMSEXPORT  _cmsWriteFloat32Number(cmsIOHANDLER* io, cmsFloat32Number n)
{
    cmsUInt32Number tmp;

    _cmsAssert(io != NULL);

    tmp = *(cmsUInt32Number*) &n;
    tmp = _cmsAdjustEndianess32(tmp);
    if (io -> Write(io, sizeof(cmsUInt32Number), &tmp) != 1)
            return FALSE;

    return TRUE;
}

cmsBool CMSEXPORT  _cmsWriteUInt64Number(cmsIOHANDLER* io, cmsUInt64Number* n)
{
    cmsUInt64Number tmp;

    _cmsAssert(io != NULL);

    _cmsAdjustEndianess64(&tmp, n);
    if (io -> Write(io, sizeof(cmsUInt64Number), &tmp) != 1)
            return FALSE;

    return TRUE;
}

cmsBool CMSEXPORT  _cmsWrite15Fixed16Number(cmsIOHANDLER* io, cmsFloat64Number n)
{
    cmsUInt32Number tmp;

    _cmsAssert(io != NULL);

    tmp = _cmsAdjustEndianess32(_cmsDoubleTo15Fixed16(n));
    if (io -> Write(io, sizeof(cmsUInt32Number), &tmp) != 1)
            return FALSE;

    return TRUE;
}

cmsBool CMSEXPORT  _cmsWriteXYZNumber(cmsIOHANDLER* io, const cmsCIEXYZ* XYZ)
{
    cmsEncodedXYZNumber xyz;

    _cmsAssert(io != NULL);
    _cmsAssert(XYZ != NULL);

    xyz.X = _cmsAdjustEndianess32(_cmsDoubleTo15Fixed16(XYZ->X));
    xyz.Y = _cmsAdjustEndianess32(_cmsDoubleTo15Fixed16(XYZ->Y));
    xyz.Z = _cmsAdjustEndianess32(_cmsDoubleTo15Fixed16(XYZ->Z));

    return io -> Write(io,  sizeof(cmsEncodedXYZNumber), &xyz);
}

// from Fixed point 8.8 to double
cmsFloat64Number CMSEXPORT _cms8Fixed8toDouble(cmsUInt16Number fixed8)
{
       cmsUInt8Number  msb, lsb;

       lsb = (cmsUInt8Number) (fixed8 & 0xff);
       msb = (cmsUInt8Number) (((cmsUInt16Number) fixed8 >> 8) & 0xff);

       return (cmsFloat64Number) ((cmsFloat64Number) msb + ((cmsFloat64Number) lsb / 256.0));
}

cmsUInt16Number CMSEXPORT _cmsDoubleTo8Fixed8(cmsFloat64Number val)
{
    cmsS15Fixed16Number GammaFixed32 = _cmsDoubleTo15Fixed16(val);
    return  (cmsUInt16Number) ((GammaFixed32 >> 8) & 0xFFFF);
}

// from Fixed point 15.16 to double
cmsFloat64Number CMSEXPORT _cms15Fixed16toDouble(cmsS15Fixed16Number fix32)
{
    cmsFloat64Number floater, sign, mid;
    int Whole, FracPart;

    sign  = (fix32 < 0 ? -1 : 1);
    fix32 = abs(fix32);

    Whole     = (cmsUInt16Number)(fix32 >> 16) & 0xffff;
    FracPart  = (cmsUInt16Number)(fix32 & 0xffff);

    mid     = (cmsFloat64Number) FracPart / 65536.0;
    floater = (cmsFloat64Number) Whole + mid;

    return sign * floater;
}

// from double to Fixed point 15.16
cmsS15Fixed16Number CMSEXPORT _cmsDoubleTo15Fixed16(cmsFloat64Number v)
{
    return ((cmsS15Fixed16Number) floor((v)*65536.0 + 0.5));
}

// Date/Time functions

void CMSEXPORT _cmsDecodeDateTimeNumber(const cmsDateTimeNumber *Source, struct tm *Dest)
{

    _cmsAssert(Dest != NULL);
    _cmsAssert(Source != NULL);

    Dest->tm_sec   = _cmsAdjustEndianess16(Source->seconds);
    Dest->tm_min   = _cmsAdjustEndianess16(Source->minutes);
    Dest->tm_hour  = _cmsAdjustEndianess16(Source->hours);
    Dest->tm_mday  = _cmsAdjustEndianess16(Source->day);
    Dest->tm_mon   = _cmsAdjustEndianess16(Source->month) - 1;
    Dest->tm_year  = _cmsAdjustEndianess16(Source->year) - 1900;
    Dest->tm_wday  = -1;
    Dest->tm_yday  = -1;
    Dest->tm_isdst = 0;
}

void CMSEXPORT _cmsEncodeDateTimeNumber(cmsDateTimeNumber *Dest, const struct tm *Source)
{
    _cmsAssert(Dest != NULL);
    _cmsAssert(Source != NULL);

    Dest->seconds = _cmsAdjustEndianess16((cmsUInt16Number) Source->tm_sec);
    Dest->minutes = _cmsAdjustEndianess16((cmsUInt16Number) Source->tm_min);
    Dest->hours   = _cmsAdjustEndianess16((cmsUInt16Number) Source->tm_hour);
    Dest->day     = _cmsAdjustEndianess16((cmsUInt16Number) Source->tm_mday);
    Dest->month   = _cmsAdjustEndianess16((cmsUInt16Number) (Source->tm_mon + 1));
    Dest->year    = _cmsAdjustEndianess16((cmsUInt16Number) (Source->tm_year + 1900));
}

// Read base and return type base
cmsTagTypeSignature CMSEXPORT _cmsReadTypeBase(cmsIOHANDLER* io)
{
    _cmsTagBase Base;

    _cmsAssert(io != NULL);

    if (io -> Read(io, &Base, sizeof(_cmsTagBase), 1) != 1)
        return (cmsTagTypeSignature) 0;

    return (cmsTagTypeSignature) _cmsAdjustEndianess32(Base.sig);
}

// Setup base marker
cmsBool  CMSEXPORT _cmsWriteTypeBase(cmsIOHANDLER* io, cmsTagTypeSignature sig)
{
    _cmsTagBase  Base;

    _cmsAssert(io != NULL);

    Base.sig = (cmsTagTypeSignature) _cmsAdjustEndianess32(sig);
    memset(&Base.reserved, 0, sizeof(Base.reserved));
    return io -> Write(io, sizeof(_cmsTagBase), &Base);
}

cmsBool CMSEXPORT _cmsReadAlignment(cmsIOHANDLER* io)
{
    cmsUInt8Number  Buffer[4];
    cmsUInt32Number NextAligned, At;
    cmsUInt32Number BytesToNextAlignedPos;

    _cmsAssert(io != NULL);

    At = io -> Tell(io);
    NextAligned = _cmsALIGNLONG(At);
    BytesToNextAlignedPos = NextAligned - At;
    if (BytesToNextAlignedPos == 0) return TRUE;
    if (BytesToNextAlignedPos > 4)  return FALSE;

    return (io ->Read(io, Buffer, BytesToNextAlignedPos, 1) == 1);
}

cmsBool CMSEXPORT _cmsWriteAlignment(cmsIOHANDLER* io)
{
    cmsUInt8Number  Buffer[4];
    cmsUInt32Number NextAligned, At;
    cmsUInt32Number BytesToNextAlignedPos;

    _cmsAssert(io != NULL);

    At = io -> Tell(io);
    NextAligned = _cmsALIGNLONG(At);
    BytesToNextAlignedPos = NextAligned - At;
    if (BytesToNextAlignedPos == 0) return TRUE;
    if (BytesToNextAlignedPos > 4)  return FALSE;

    memset(Buffer, 0, BytesToNextAlignedPos);
    return io -> Write(io, BytesToNextAlignedPos, Buffer);
}


// To deal with text streams. 2K at most
cmsBool CMSEXPORT _cmsIOPrintf(cmsIOHANDLER* io, const char* frm, ...)
{
    va_list args;
    int len;
    cmsUInt8Number Buffer[2048];
    cmsBool rc;

    _cmsAssert(io != NULL);
    _cmsAssert(frm != NULL);

    va_start(args, frm);

    len = vsnprintf((char*) Buffer, 2047, frm, args);
    if (len < 0) return FALSE;   // Truncated, which is a fatal error for us

    rc = io ->Write(io, len, Buffer);

    va_end(args);

    return rc;
}


// Plugin memory management -------------------------------------------------------------------------------------------------

static _cmsSubAllocator* PluginPool = NULL;

// Specialized malloc for plug-ins, that is freed upon exit.
void* _cmsPluginMalloc(cmsUInt32Number size)
{
    if (PluginPool == NULL)
        PluginPool = _cmsCreateSubAlloc(0, 4*1024);

    return _cmsSubAlloc(PluginPool, size);
}


// Main plug-in dispatcher
cmsBool CMSEXPORT cmsPlugin(void* Plug_in)
{
    cmsPluginBase* Plugin;

    for (Plugin = (cmsPluginBase*) Plug_in;
         Plugin != NULL;
         Plugin = Plugin -> Next) {

            if (Plugin -> Magic != cmsPluginMagicNumber) {
                cmsSignalError(0, cmsERROR_UNKNOWN_EXTENSION, "Unrecognized plugin");
                return FALSE;
            }

            if (Plugin ->ExpectedVersion > LCMS_VERSION) {
                cmsSignalError(0, cmsERROR_UNKNOWN_EXTENSION, "plugin needs Little CMS %d, current  version is %d",
                    Plugin ->ExpectedVersion, LCMS_VERSION);
                return FALSE;
            }

            switch (Plugin -> Type) {

                case cmsPluginMemHandlerSig:
                    if (!_cmsRegisterMemHandlerPlugin(Plugin)) return FALSE;
                    break;

                case cmsPluginInterpolationSig:
                    if (!_cmsRegisterInterpPlugin(Plugin)) return FALSE;
                    break;

                case cmsPluginTagTypeSig:
                    if (!_cmsRegisterTagTypePlugin(Plugin)) return FALSE;
                    break;

                case cmsPluginTagSig:
                    if (!_cmsRegisterTagPlugin(Plugin)) return FALSE;
                    break;

                case cmsPluginFormattersSig:
                    if (!_cmsRegisterFormattersPlugin(Plugin)) return FALSE;
                    break;

                case cmsPluginRenderingIntentSig:
                    if (!_cmsRegisterRenderingIntentPlugin(Plugin)) return FALSE;
                    break;

                case cmsPluginParametricCurveSig:
                    if (!_cmsRegisterParametricCurvesPlugin(Plugin)) return FALSE;
                    break;

                case cmsPluginMultiProcessElementSig:
                    if (!_cmsRegisterMultiProcessElementPlugin(Plugin)) return FALSE;
                    break;

                case cmsPluginOptimizationSig:
                    if (!_cmsRegisterOptimizationPlugin(Plugin)) return FALSE;
                    break;

                case cmsPluginTransformSig:
                    if (!_cmsRegisterTransformPlugin(Plugin)) return FALSE;
                    break;

                default:
                    cmsSignalError(0, cmsERROR_UNKNOWN_EXTENSION, "Unrecognized plugin type '%X'", Plugin -> Type);
                    return FALSE;
            }
    }

    // Keep a reference to the plug-in
    return TRUE;
}


// Revert all plug-ins to default
void CMSEXPORT cmsUnregisterPlugins(void)
{
    _cmsRegisterMemHandlerPlugin(NULL);
    _cmsRegisterInterpPlugin(NULL);
    _cmsRegisterTagTypePlugin(NULL);
    _cmsRegisterTagPlugin(NULL);
    _cmsRegisterFormattersPlugin(NULL);
    _cmsRegisterRenderingIntentPlugin(NULL);
    _cmsRegisterParametricCurvesPlugin(NULL);
    _cmsRegisterMultiProcessElementPlugin(NULL);
    _cmsRegisterOptimizationPlugin(NULL);
    _cmsRegisterTransformPlugin(NULL);

    if (PluginPool != NULL)
        _cmsSubAllocDestroy(PluginPool);

    PluginPool = NULL;
}
