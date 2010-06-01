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

// Version 1.18

#ifndef __cms_H

// ********** Configuration toggles ****************************************

//   Optimization mode.
//
// Note that USE_ASSEMBLER Is fastest by far, but it is limited to Pentium.
// USE_FLOAT are the generic floating-point routines. USE_C should work on
// virtually any machine.

//#define USE_FLOAT        1
// #define USE_C            1
#define USE_ASSEMBLER    1

// Define this if you are using this package as a DLL (windows only)

// #define LCMS_DLL     1
// #define LCMS_DLL_BUILD   1

// Uncomment if you are trying the engine in a non-windows environment
// like linux, SGI, VAX, FreeBSD, BeOS, etc.
#define NON_WINDOWS  1

// Uncomment this one if you are using big endian machines (only meaningful
// when NON_WINDOWS is used)
// #define USE_BIG_ENDIAN   1

// Uncomment this one if your compiler/machine does support the
// "long long" type This will speedup fixed point math. (USE_C only)
#define USE_INT64        1

// Some machines does not have a reliable 'swab' function. Usually
// leave commented unless the testbed diagnoses the contrary.
// #define USE_CUSTOM_SWAB   1

// Uncomment this if your compiler supports inline
#define USE_INLINE  1

// Uncomment this if your compiler doesn't work with fast floor function
// #define USE_DEFAULT_FLOOR_CONVERSION  1

// Uncomment this line on multithreading environments
// #define USE_PTHREADS    1

// Uncomment this line if you want lcms to use the black point tag in profile,
// if commented, lcms will compute the black point by its own.
// It is safer to leve it commented out
// #define HONOR_BLACK_POINT_TAG    1

// ********** End of configuration toggles ******************************

#define LCMS_VERSION        118

// Microsoft VisualC++

// Deal with Microsoft's attempt at deprecating C standard runtime functions
#ifdef _MSC_VER
#    undef NON_WINDOWS
#    if (_MSC_VER >= 1400)
#      ifndef _CRT_SECURE_NO_DEPRECATE
#        define _CRT_SECURE_NO_DEPRECATE 1
#      endif
#    endif
#endif

// Borland C

#ifdef __BORLANDC__
#    undef NON_WINDOWS
#endif

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <assert.h>
#include <stdarg.h>
#include <time.h>

// Metroworks CodeWarrior
#ifdef __MWERKS__
#   define unlink remove
#   if WIN32
#       define USE_CUSTOM_SWAB 1
#       undef  NON_WINDOWS
#   else
#       define NON_WINDOWS   1
#   endif
#endif


// Here comes the Non-Windows settings

#ifdef NON_WINDOWS

// Non windows environments. Also avoid indentation on includes.

#ifdef USE_PTHREADS
#   include <pthread.h>
typedef    pthread_rwlock_t      LCMS_RWLOCK_T;
#   define LCMS_CREATE_LOCK(x)       pthread_rwlock_init((x), NULL)
#   define LCMS_FREE_LOCK(x)         pthread_rwlock_destroy((x))
#   define LCMS_READ_LOCK(x)             pthread_rwlock_rdlock((x))
#   define LCMS_WRITE_LOCK(x)        pthread_rwlock_wrlock((x))
#   define LCMS_UNLOCK(x)            pthread_rwlock_unlock((x))
#endif

#undef LCMS_DLL

#ifdef  USE_ASSEMBLER
#  undef  USE_ASSEMBLER
#  define USE_C               1
#endif

#ifdef _HOST_BIG_ENDIAN
#   define USE_BIG_ENDIAN      1
#endif

#if defined(__sgi__) || defined(__sgi) || defined(__powerpc__) || defined(sparc) || defined(__ppc__) || defined(__s390__) || defined(__s390x__)
#   define USE_BIG_ENDIAN      1
#endif

#if TARGET_CPU_PPC
#   define USE_BIG_ENDIAN   1
#endif

#if macintosh
# ifndef __LITTLE_ENDIAN__
#   define USE_BIG_ENDIAN      1
# endif
#endif

#ifdef __BIG_ENDIAN__
#   define USE_BIG_ENDIAN      1
#endif

#ifdef WORDS_BIGENDIAN
#   define USE_BIG_ENDIAN      1
#endif

#if defined(__OpenBSD__) || defined(__NetBSD__) || defined(__FreeBSD__)
#  include <sys/types.h>
#  define USE_INT64           1
#  define LCMSSLONGLONG       int64_t
#  define LCMSULONGLONG       u_int64_t
#endif

#ifdef USE_INT64
#   ifndef LCMSULONGLONG
#       define LCMSULONGLONG unsigned long long
#       define LCMSSLONGLONG long long
#   endif
#endif

#if !defined(__INTEGRITY)
#   include <memory.h>
#endif

#include <string.h>

#if defined(__GNUC__) || defined(__FreeBSD__)
#   include <unistd.h>
#endif

#ifndef LCMS_WIN_TYPES_ALREADY_DEFINED

typedef unsigned char BYTE, *LPBYTE;
typedef unsigned short WORD, *LPWORD;
typedef unsigned long DWORD, *LPDWORD;
typedef char *LPSTR;
typedef void *LPVOID;

#define ZeroMemory(p,l)     memset((p),0,(l))
#define CopyMemory(d,s,l)   memcpy((d),(s),(l))
#define FAR

#ifndef stricmp
#   define stricmp strcasecmp
#endif


#ifndef FALSE
#       define FALSE 0
#endif
#ifndef TRUE
#       define TRUE  1
#endif

#define LOWORD(l)    ((WORD)(l))
#define HIWORD(l)    ((WORD)((DWORD)(l) >> 16))

#ifndef MAX_PATH
#       define MAX_PATH     (256)
#endif

#define cdecl
#endif

// The specification for "inline" is section 6.7.4 of the C99 standard (ISO/IEC 9899:1999).

#define LCMS_INLINE static inline

#else

// Win32 stuff

#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif

#include <windows.h>

#ifdef _WIN64
# ifdef USE_ASSEMBLER
#    undef  USE_ASSEMBLER
#    define USE_C           1
# endif
#endif

#ifdef  USE_INT64
#  ifndef LCMSULONGLONG
#    define LCMSULONGLONG unsigned __int64
#    define LCMSSLONGLONG __int64
#  endif
#endif

// This works for both VC & BorlandC
#define LCMS_INLINE __inline

#ifdef USE_PTHREADS
typedef CRITICAL_SECTION LCMS_RWLOCK_T;
#   define LCMS_CREATE_LOCK(x)       InitializeCriticalSection((x))
#   define LCMS_FREE_LOCK(x)         DeleteCriticalSection((x))
#   define LCMS_READ_LOCK(x)             EnterCriticalSection((x))
#   define LCMS_WRITE_LOCK(x)        EnterCriticalSection((x))
#   define LCMS_UNLOCK(x)            LeaveCriticalSection((x))
#endif

#endif

#ifndef USE_PTHREADS
typedef int LCMS_RWLOCK_T;
#   define LCMS_CREATE_LOCK(x)
#   define LCMS_FREE_LOCK(x)
#   define LCMS_READ_LOCK(x)
#   define LCMS_WRITE_LOCK(x)
#   define LCMS_UNLOCK(x)
#endif

// Base types

typedef int   LCMSBOOL;
typedef void* LCMSHANDLE;

#include "icc34.h"          // ICC header file


// Some tag & type additions

#define lcmsSignature                  ((icSignature)           0x6c636d73L)

#define icSigLuvKData                  ((icColorSpaceSignature) 0x4C75764BL)  // 'LuvK'

#define icSigHexachromeData            ((icColorSpaceSignature) 0x4d434836L)  // MCH6
#define icSigHeptachromeData           ((icColorSpaceSignature) 0x4d434837L)  // MCH7
#define icSigOctachromeData            ((icColorSpaceSignature) 0x4d434838L)  // MCH8

#define icSigMCH5Data                  ((icColorSpaceSignature) 0x4d434835L)  // MCH5
#define icSigMCH6Data                  ((icColorSpaceSignature) 0x4d434836L)  // MCH6
#define icSigMCH7Data                  ((icColorSpaceSignature) 0x4d434837L)  // MCH7
#define icSigMCH8Data                  ((icColorSpaceSignature) 0x4d434838L)  // MCH8
#define icSigMCH9Data                  ((icColorSpaceSignature) 0x4d434839L)  // MCH9
#define icSigMCHAData                  ((icColorSpaceSignature) 0x4d434841L)  // MCHA
#define icSigMCHBData                  ((icColorSpaceSignature) 0x4d434842L)  // MCHB
#define icSigMCHCData                  ((icColorSpaceSignature) 0x4d434843L)  // MCHC
#define icSigMCHDData                  ((icColorSpaceSignature) 0x4d434844L)  // MCHD
#define icSigMCHEData                  ((icColorSpaceSignature) 0x4d434845L)  // MCHE
#define icSigMCHFData                  ((icColorSpaceSignature) 0x4d434846L)  // MCHF

#define icSigChromaticityTag            ((icTagSignature) 0x6368726dL) // As per Addendum 2 to Spec. ICC.1:1998-09
#define icSigChromaticAdaptationTag     ((icTagSignature) 0x63686164L) // 'chad'
#define icSigColorantTableTag           ((icTagSignature) 0x636c7274L) // 'clrt'
#define icSigColorantTableOutTag        ((icTagSignature) 0x636c6f74L) // 'clot'

#define icSigParametricCurveType        ((icTagTypeSignature) 0x70617261L)  // parametric (ICC 4.0)
#define icSigMultiLocalizedUnicodeType  ((icTagTypeSignature) 0x6D6C7563L)
#define icSigS15Fixed16ArrayType        ((icTagTypeSignature) 0x73663332L)
#define icSigChromaticityType           ((icTagTypeSignature) 0x6368726dL)
#define icSiglutAtoBType                ((icTagTypeSignature) 0x6d414220L)  // mAB
#define icSiglutBtoAType                ((icTagTypeSignature) 0x6d424120L)  // mBA
#define icSigColorantTableType          ((icTagTypeSignature) 0x636c7274L)  // clrt


typedef struct {
    icUInt8Number       gridPoints[16]; // Number of grid points in each dimension.
    icUInt8Number       prec;           // Precision of data elements in bytes.
    icUInt8Number       pad1;
    icUInt8Number       pad2;
    icUInt8Number       pad3;
    /*icUInt8Number     data[icAny];     Data follows see spec for size */
} icCLutStruct;

// icLutAtoB
typedef struct {
    icUInt8Number       inputChan;      // Number of input channels
    icUInt8Number       outputChan;     // Number of output channels
    icUInt8Number       pad1;
    icUInt8Number       pad2;
    icUInt32Number      offsetB;        // Offset to first "B" curve
    icUInt32Number      offsetMat;      // Offset to matrix
    icUInt32Number      offsetM;        // Offset to first "M" curve
    icUInt32Number      offsetC;        // Offset to CLUT
    icUInt32Number      offsetA;        // Offset to first "A" curve
    /*icUInt8Number     data[icAny];     Data follows see spec for size */
} icLutAtoB;

// icLutBtoA
typedef struct {
    icUInt8Number       inputChan;      // Number of input channels
    icUInt8Number       outputChan;     // Number of output channels
    icUInt8Number       pad1;
    icUInt8Number       pad2;
    icUInt32Number      offsetB;        // Offset to first "B" curve
    icUInt32Number      offsetMat;      // Offset to matrix
    icUInt32Number      offsetM;        // Offset to first "M" curve
    icUInt32Number      offsetC;        // Offset to CLUT
    icUInt32Number      offsetA;        // Offset to first "A" curve
    /*icUInt8Number     data[icAny];     Data follows see spec for size */
} icLutBtoA;





#ifdef __cplusplus
extern "C" {
#endif

// Calling convention

#ifdef NON_WINDOWS
#  define LCMSEXPORT
#  define LCMSAPI
#else
# ifdef LCMS_DLL
#   ifdef __BORLANDC__
#      define LCMSEXPORT __stdcall _export
#      define LCMSAPI
#   else
       // VC++
#       define LCMSEXPORT  _stdcall
#       ifdef LCMS_DLL_BUILD
#           define LCMSAPI     __declspec(dllexport)
#       else
#           define LCMSAPI     __declspec(dllimport)
#       endif
#   endif
# else
#       define LCMSEXPORT cdecl
#       define LCMSAPI
# endif
#endif

#ifdef  USE_ASSEMBLER
#ifdef __BORLANDC__

#      define ASM     asm
#      define RET(v)  return(v)
#else
      // VC++
#      define ASM     __asm
#      define RET(v)  return
#endif
#endif

#ifdef _MSC_VER
#ifndef  stricmp
#      define stricmp _stricmp
#endif
#ifndef unlink
#      define unlink  _unlink
#endif
#ifndef swab
#      define swab    _swab
#endif
#ifndef itoa
#       define itoa   _itoa
#endif
#ifndef fileno
#       define fileno   _fileno
#endif
#ifndef strupr
#       define strupr   _strupr
#endif
#ifndef hypot
#       define hypot    _hypot
#endif
#ifndef snprintf
#       define snprintf  _snprintf
#endif
#ifndef vsnprintf
#       define vsnprintf  _vsnprintf
#endif


#endif


#ifndef M_PI
#       define M_PI    3.14159265358979323846
#endif

#ifndef LOGE
#       define LOGE   0.4342944819
#endif

// ********** Little cms API ***************************************************

typedef LCMSHANDLE cmsHPROFILE;        // Opaque typedefs to hide internals
typedef LCMSHANDLE cmsHTRANSFORM;

#define MAXCHANNELS  16                // Maximum number of channels

// Format of pixel is defined by one DWORD, using bit fields as follows
//
//            D TTTTT U Y F P X S EEE CCCC BBB
//
//            D: Use dither (8 bits only)
//            T: Pixeltype
//            F: Flavor  0=MinIsBlack(Chocolate) 1=MinIsWhite(Vanilla)
//            P: Planar? 0=Chunky, 1=Planar
//            X: swap 16 bps endianess?
//            S: Do swap? ie, BGR, KYMC
//            E: Extra samples
//            C: Channels (Samples per pixel)
//            B: Bytes per sample
//            Y: Swap first - changes ABGR to BGRA and KCMY to CMYK


#define DITHER_SH(s)           ((s) << 22)
#define COLORSPACE_SH(s)       ((s) << 16)
#define SWAPFIRST_SH(s)        ((s) << 14)
#define FLAVOR_SH(s)           ((s) << 13)
#define PLANAR_SH(p)           ((p) << 12)
#define ENDIAN16_SH(e)         ((e) << 11)
#define DOSWAP_SH(e)           ((e) << 10)
#define EXTRA_SH(e)            ((e) << 7)
#define CHANNELS_SH(c)         ((c) << 3)
#define BYTES_SH(b)            (b)

// Pixel types

#define PT_ANY       0    // Don't check colorspace
                          // 1 & 2 are reserved
#define PT_GRAY      3
#define PT_RGB       4
#define PT_CMY       5
#define PT_CMYK      6
#define PT_YCbCr     7
#define PT_YUV       8      // Lu'v'
#define PT_XYZ       9
#define PT_Lab       10
#define PT_YUVK      11     // Lu'v'K
#define PT_HSV       12
#define PT_HLS       13
#define PT_Yxy       14
#define PT_HiFi      15
#define PT_HiFi7     16
#define PT_HiFi8     17
#define PT_HiFi9     18
#define PT_HiFi10    19
#define PT_HiFi11    20
#define PT_HiFi12    21
#define PT_HiFi13    22
#define PT_HiFi14    23
#define PT_HiFi15    24

#define NOCOLORSPACECHECK(x)    ((x) & 0xFFFF)

// Some (not all!) representations

#ifndef TYPE_RGB_8      // TYPE_RGB_8 is a very common identifier, so don't include ours
                        // if user has it already defined.

#define TYPE_GRAY_8            (COLORSPACE_SH(PT_GRAY)|CHANNELS_SH(1)|BYTES_SH(1))
#define TYPE_GRAY_8_REV        (COLORSPACE_SH(PT_GRAY)|CHANNELS_SH(1)|BYTES_SH(1)|FLAVOR_SH(1))
#define TYPE_GRAY_16           (COLORSPACE_SH(PT_GRAY)|CHANNELS_SH(1)|BYTES_SH(2))
#define TYPE_GRAY_16_REV       (COLORSPACE_SH(PT_GRAY)|CHANNELS_SH(1)|BYTES_SH(2)|FLAVOR_SH(1))
#define TYPE_GRAY_16_SE        (COLORSPACE_SH(PT_GRAY)|CHANNELS_SH(1)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_GRAYA_8           (COLORSPACE_SH(PT_GRAY)|EXTRA_SH(1)|CHANNELS_SH(1)|BYTES_SH(1))
#define TYPE_GRAYA_16          (COLORSPACE_SH(PT_GRAY)|EXTRA_SH(1)|CHANNELS_SH(1)|BYTES_SH(2))
#define TYPE_GRAYA_16_SE       (COLORSPACE_SH(PT_GRAY)|EXTRA_SH(1)|CHANNELS_SH(1)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_GRAYA_8_PLANAR    (COLORSPACE_SH(PT_GRAY)|EXTRA_SH(1)|CHANNELS_SH(1)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_GRAYA_16_PLANAR   (COLORSPACE_SH(PT_GRAY)|EXTRA_SH(1)|CHANNELS_SH(1)|BYTES_SH(2)|PLANAR_SH(1))

#define TYPE_RGB_8             (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(1))
#define TYPE_RGB_8_PLANAR      (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_BGR_8             (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_BGR_8_PLANAR      (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(1)|DOSWAP_SH(1)|PLANAR_SH(1))
#define TYPE_RGB_16            (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(2))
#define TYPE_RGB_16_PLANAR     (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(2)|PLANAR_SH(1))
#define TYPE_RGB_16_SE         (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_BGR_16            (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_BGR_16_PLANAR     (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(2)|DOSWAP_SH(1)|PLANAR_SH(1))
#define TYPE_BGR_16_SE         (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))

#define TYPE_RGBA_8            (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(1))
#define TYPE_RGBA_8_PLANAR     (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_RGBA_16           (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(2))
#define TYPE_RGBA_16_PLANAR    (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(2)|PLANAR_SH(1))
#define TYPE_RGBA_16_SE        (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(2)|ENDIAN16_SH(1))

#define TYPE_ARGB_8            (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(1)|SWAPFIRST_SH(1))
#define TYPE_ARGB_16           (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(2)|SWAPFIRST_SH(1))

#define TYPE_ABGR_8            (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_ABGR_16           (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_ABGR_16_PLANAR    (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(2)|DOSWAP_SH(1)|PLANAR_SH(1))
#define TYPE_ABGR_16_SE        (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))

#define TYPE_BGRA_8            (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(1)|DOSWAP_SH(1)|SWAPFIRST_SH(1))
#define TYPE_BGRA_16           (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(2)|DOSWAP_SH(1)|SWAPFIRST_SH(1))
#define TYPE_BGRA_16_SE        (COLORSPACE_SH(PT_RGB)|EXTRA_SH(1)|CHANNELS_SH(3)|BYTES_SH(2)|ENDIAN16_SH(1)|SWAPFIRST_SH(1))

#define TYPE_CMY_8             (COLORSPACE_SH(PT_CMY)|CHANNELS_SH(3)|BYTES_SH(1))
#define TYPE_CMY_8_PLANAR      (COLORSPACE_SH(PT_CMY)|CHANNELS_SH(3)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_CMY_16            (COLORSPACE_SH(PT_CMY)|CHANNELS_SH(3)|BYTES_SH(2))
#define TYPE_CMY_16_PLANAR     (COLORSPACE_SH(PT_CMY)|CHANNELS_SH(3)|BYTES_SH(2)|PLANAR_SH(1))
#define TYPE_CMY_16_SE         (COLORSPACE_SH(PT_CMY)|CHANNELS_SH(3)|BYTES_SH(2)|ENDIAN16_SH(1))

#define TYPE_CMYK_8            (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(1))
#define TYPE_CMYKA_8           (COLORSPACE_SH(PT_CMYK)|EXTRA_SH(1)|CHANNELS_SH(4)|BYTES_SH(1))
#define TYPE_CMYK_8_REV        (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(1)|FLAVOR_SH(1))
#define TYPE_YUVK_8            TYPE_CMYK_8_REV
#define TYPE_CMYK_8_PLANAR     (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_CMYK_16           (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(2))
#define TYPE_CMYK_16_REV       (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(2)|FLAVOR_SH(1))
#define TYPE_YUVK_16           TYPE_CMYK_16_REV
#define TYPE_CMYK_16_PLANAR    (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(2)|PLANAR_SH(1))
#define TYPE_CMYK_16_SE        (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(2)|ENDIAN16_SH(1))

#define TYPE_KYMC_8            (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_KYMC_16           (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_KYMC_16_SE        (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))

#define TYPE_KCMY_8            (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(1)|SWAPFIRST_SH(1))
#define TYPE_KCMY_8_REV        (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(1)|FLAVOR_SH(1)|SWAPFIRST_SH(1))
#define TYPE_KCMY_16           (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(2)|SWAPFIRST_SH(1))
#define TYPE_KCMY_16_REV       (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(2)|FLAVOR_SH(1)|SWAPFIRST_SH(1))
#define TYPE_KCMY_16_SE        (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(2)|ENDIAN16_SH(1)|SWAPFIRST_SH(1))


// HiFi separations, Thanks to Steven Greaves for providing the code,
// the colorspace is not checked
#define TYPE_CMYK5_8           (CHANNELS_SH(5)|BYTES_SH(1))
#define TYPE_CMYK5_16          (CHANNELS_SH(5)|BYTES_SH(2))
#define TYPE_CMYK5_16_SE       (CHANNELS_SH(5)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_KYMC5_8           (CHANNELS_SH(5)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_KYMC5_16          (CHANNELS_SH(5)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_KYMC5_16_SE       (CHANNELS_SH(5)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))

#define TYPE_CMYKcm_8          (CHANNELS_SH(6)|BYTES_SH(1))
#define TYPE_CMYKcm_8_PLANAR   (CHANNELS_SH(6)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_CMYKcm_16         (CHANNELS_SH(6)|BYTES_SH(2))
#define TYPE_CMYKcm_16_PLANAR  (CHANNELS_SH(6)|BYTES_SH(2)|PLANAR_SH(1))
#define TYPE_CMYKcm_16_SE      (CHANNELS_SH(6)|BYTES_SH(2)|ENDIAN16_SH(1))

// Separations with more than 6 channels aren't very standarized,
// Except most start with CMYK and add other colors, so I just used
// then total number of channels after CMYK i.e CMYK8_8

#define TYPE_CMYK7_8           (CHANNELS_SH(7)|BYTES_SH(1))
#define TYPE_CMYK7_16          (CHANNELS_SH(7)|BYTES_SH(2))
#define TYPE_CMYK7_16_SE       (CHANNELS_SH(7)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_KYMC7_8           (CHANNELS_SH(7)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_KYMC7_16          (CHANNELS_SH(7)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_KYMC7_16_SE       (CHANNELS_SH(7)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))
#define TYPE_CMYK8_8           (CHANNELS_SH(8)|BYTES_SH(1))
#define TYPE_CMYK8_16          (CHANNELS_SH(8)|BYTES_SH(2))
#define TYPE_CMYK8_16_SE       (CHANNELS_SH(8)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_KYMC8_8           (CHANNELS_SH(8)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_KYMC8_16          (CHANNELS_SH(8)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_KYMC8_16_SE       (CHANNELS_SH(8)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))
#define TYPE_CMYK9_8           (CHANNELS_SH(9)|BYTES_SH(1))
#define TYPE_CMYK9_16          (CHANNELS_SH(9)|BYTES_SH(2))
#define TYPE_CMYK9_16_SE       (CHANNELS_SH(9)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_KYMC9_8           (CHANNELS_SH(9)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_KYMC9_16          (CHANNELS_SH(9)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_KYMC9_16_SE       (CHANNELS_SH(9)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))
#define TYPE_CMYK10_8          (CHANNELS_SH(10)|BYTES_SH(1))
#define TYPE_CMYK10_16         (CHANNELS_SH(10)|BYTES_SH(2))
#define TYPE_CMYK10_16_SE      (CHANNELS_SH(10)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_KYMC10_8          (CHANNELS_SH(10)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_KYMC10_16         (CHANNELS_SH(10)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_KYMC10_16_SE      (CHANNELS_SH(10)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))
#define TYPE_CMYK11_8          (CHANNELS_SH(11)|BYTES_SH(1))
#define TYPE_CMYK11_16         (CHANNELS_SH(11)|BYTES_SH(2))
#define TYPE_CMYK11_16_SE      (CHANNELS_SH(11)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_KYMC11_8          (CHANNELS_SH(11)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_KYMC11_16         (CHANNELS_SH(11)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_KYMC11_16_SE      (CHANNELS_SH(11)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))
#define TYPE_CMYK12_8          (CHANNELS_SH(12)|BYTES_SH(1))
#define TYPE_CMYK12_16         (CHANNELS_SH(12)|BYTES_SH(2))
#define TYPE_CMYK12_16_SE      (CHANNELS_SH(12)|BYTES_SH(2)|ENDIAN16_SH(1))
#define TYPE_KYMC12_8          (CHANNELS_SH(12)|BYTES_SH(1)|DOSWAP_SH(1))
#define TYPE_KYMC12_16         (CHANNELS_SH(12)|BYTES_SH(2)|DOSWAP_SH(1))
#define TYPE_KYMC12_16_SE      (CHANNELS_SH(12)|BYTES_SH(2)|DOSWAP_SH(1)|ENDIAN16_SH(1))

// Colorimetric

#define TYPE_XYZ_16            (COLORSPACE_SH(PT_XYZ)|CHANNELS_SH(3)|BYTES_SH(2))
#define TYPE_Lab_8             (COLORSPACE_SH(PT_Lab)|CHANNELS_SH(3)|BYTES_SH(1))
#define TYPE_ALab_8            (COLORSPACE_SH(PT_Lab)|CHANNELS_SH(3)|BYTES_SH(1)|EXTRA_SH(1)|DOSWAP_SH(1))
#define TYPE_Lab_16            (COLORSPACE_SH(PT_Lab)|CHANNELS_SH(3)|BYTES_SH(2))
#define TYPE_Yxy_16            (COLORSPACE_SH(PT_Yxy)|CHANNELS_SH(3)|BYTES_SH(2))

// YCbCr

#define TYPE_YCbCr_8           (COLORSPACE_SH(PT_YCbCr)|CHANNELS_SH(3)|BYTES_SH(1))
#define TYPE_YCbCr_8_PLANAR    (COLORSPACE_SH(PT_YCbCr)|CHANNELS_SH(3)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_YCbCr_16          (COLORSPACE_SH(PT_YCbCr)|CHANNELS_SH(3)|BYTES_SH(2))
#define TYPE_YCbCr_16_PLANAR   (COLORSPACE_SH(PT_YCbCr)|CHANNELS_SH(3)|BYTES_SH(2)|PLANAR_SH(1))
#define TYPE_YCbCr_16_SE       (COLORSPACE_SH(PT_YCbCr)|CHANNELS_SH(3)|BYTES_SH(2)|ENDIAN16_SH(1))

// YUV

#define TYPE_YUV_8           (COLORSPACE_SH(PT_YUV)|CHANNELS_SH(3)|BYTES_SH(1))
#define TYPE_YUV_8_PLANAR    (COLORSPACE_SH(PT_YUV)|CHANNELS_SH(3)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_YUV_16          (COLORSPACE_SH(PT_YUV)|CHANNELS_SH(3)|BYTES_SH(2))
#define TYPE_YUV_16_PLANAR   (COLORSPACE_SH(PT_YUV)|CHANNELS_SH(3)|BYTES_SH(2)|PLANAR_SH(1))
#define TYPE_YUV_16_SE       (COLORSPACE_SH(PT_YUV)|CHANNELS_SH(3)|BYTES_SH(2)|ENDIAN16_SH(1))

// HLS

#define TYPE_HLS_8           (COLORSPACE_SH(PT_HLS)|CHANNELS_SH(3)|BYTES_SH(1))
#define TYPE_HLS_8_PLANAR    (COLORSPACE_SH(PT_HLS)|CHANNELS_SH(3)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_HLS_16          (COLORSPACE_SH(PT_HLS)|CHANNELS_SH(3)|BYTES_SH(2))
#define TYPE_HLS_16_PLANAR   (COLORSPACE_SH(PT_HLS)|CHANNELS_SH(3)|BYTES_SH(2)|PLANAR_SH(1))
#define TYPE_HLS_16_SE       (COLORSPACE_SH(PT_HLS)|CHANNELS_SH(3)|BYTES_SH(2)|ENDIAN16_SH(1))


// HSV

#define TYPE_HSV_8           (COLORSPACE_SH(PT_HSV)|CHANNELS_SH(3)|BYTES_SH(1))
#define TYPE_HSV_8_PLANAR    (COLORSPACE_SH(PT_HSV)|CHANNELS_SH(3)|BYTES_SH(1)|PLANAR_SH(1))
#define TYPE_HSV_16          (COLORSPACE_SH(PT_HSV)|CHANNELS_SH(3)|BYTES_SH(2))
#define TYPE_HSV_16_PLANAR   (COLORSPACE_SH(PT_HSV)|CHANNELS_SH(3)|BYTES_SH(2)|PLANAR_SH(1))
#define TYPE_HSV_16_SE       (COLORSPACE_SH(PT_HSV)|CHANNELS_SH(3)|BYTES_SH(2)|ENDIAN16_SH(1))

// Named color index. Only 16 bits allowed (don't check colorspace)

#define TYPE_NAMED_COLOR_INDEX   (CHANNELS_SH(1)|BYTES_SH(2))

// Double values. Painful slow, but sometimes helpful. NOTE THAT 'BYTES' FIELD IS SET TO ZERO!

#define TYPE_XYZ_DBL        (COLORSPACE_SH(PT_XYZ)|CHANNELS_SH(3)|BYTES_SH(0))
#define TYPE_Lab_DBL        (COLORSPACE_SH(PT_Lab)|CHANNELS_SH(3)|BYTES_SH(0))
#define TYPE_GRAY_DBL       (COLORSPACE_SH(PT_GRAY)|CHANNELS_SH(1)|BYTES_SH(0))
#define TYPE_RGB_DBL        (COLORSPACE_SH(PT_RGB)|CHANNELS_SH(3)|BYTES_SH(0))
#define TYPE_CMYK_DBL       (COLORSPACE_SH(PT_CMYK)|CHANNELS_SH(4)|BYTES_SH(0))

#endif


// Gamma table parameters

typedef struct {

    unsigned int Crc32;  // Has my table been touched?

    // Keep initial parameters for further serialization

    int          Type;
    double       Params[10];

    }  LCMSGAMMAPARAMS, FAR* LPLCMSGAMMAPARAMS;

// Gamma tables.

typedef struct {

    LCMSGAMMAPARAMS Seed;       // Parameters used for table creation

    // Table-based representation follows

    int  nEntries;
    WORD GammaTable[1];

    } GAMMATABLE;

typedef GAMMATABLE FAR* LPGAMMATABLE;

// Sampled curves (1D)
typedef struct {

    int     nItems;
    double* Values;

    } SAMPLEDCURVE;

typedef SAMPLEDCURVE FAR* LPSAMPLEDCURVE;

// Vectors
typedef struct {                // Float Vector

    double n[3];

    } VEC3;

typedef VEC3 FAR* LPVEC3;


typedef struct {                // Matrix

    VEC3 v[3];

    } MAT3;

typedef MAT3 FAR* LPMAT3;

// Colorspace values
typedef struct {

        double X;
        double Y;
        double Z;

    } cmsCIEXYZ;

typedef cmsCIEXYZ FAR* LPcmsCIEXYZ;

typedef struct {

        double x;
        double y;
        double Y;

    } cmsCIExyY;

typedef cmsCIExyY FAR* LPcmsCIExyY;

typedef struct {

        double L;
        double a;
        double b;

    } cmsCIELab;

typedef cmsCIELab FAR* LPcmsCIELab;

typedef struct {

        double L;
        double C;
        double h;

    } cmsCIELCh;

typedef cmsCIELCh FAR* LPcmsCIELCh;

typedef struct {

        double J;
        double C;
        double h;

    } cmsJCh;

typedef cmsJCh FAR* LPcmsJCh;

// Primaries
typedef struct {

        cmsCIEXYZ  Red;
        cmsCIEXYZ  Green;
        cmsCIEXYZ  Blue;

    } cmsCIEXYZTRIPLE;

typedef cmsCIEXYZTRIPLE FAR* LPcmsCIEXYZTRIPLE;


typedef struct {

        cmsCIExyY  Red;
        cmsCIExyY  Green;
        cmsCIExyY  Blue;

    } cmsCIExyYTRIPLE;

typedef cmsCIExyYTRIPLE FAR* LPcmsCIExyYTRIPLE;



// Following ICC spec

#define D50X  (0.9642)
#define D50Y  (1.0)
#define D50Z  (0.8249)

#define PERCEPTUAL_BLACK_X  (0.00336)
#define PERCEPTUAL_BLACK_Y  (0.0034731)
#define PERCEPTUAL_BLACK_Z  (0.00287)

// Does return pointers to constant structs

LCMSAPI LPcmsCIEXYZ LCMSEXPORT cmsD50_XYZ(void);
LCMSAPI LPcmsCIExyY LCMSEXPORT cmsD50_xyY(void);


// Input/Output

LCMSAPI cmsHPROFILE   LCMSEXPORT cmsOpenProfileFromFile(const char *ICCProfile, const char *sAccess);
LCMSAPI cmsHPROFILE   LCMSEXPORT cmsOpenProfileFromMem(LPVOID MemPtr, DWORD dwSize);
LCMSAPI LCMSBOOL      LCMSEXPORT cmsCloseProfile(cmsHPROFILE hProfile);

// Predefined run-time profiles

LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreateRGBProfile(LPcmsCIExyY WhitePoint,
                                        LPcmsCIExyYTRIPLE Primaries,
                                        LPGAMMATABLE TransferFunction[3]);

LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreateGrayProfile(LPcmsCIExyY WhitePoint,
                                              LPGAMMATABLE TransferFunction);

LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreateLinearizationDeviceLink(icColorSpaceSignature ColorSpace,
                                                        LPGAMMATABLE TransferFunctions[]);

LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreateInkLimitingDeviceLink(icColorSpaceSignature ColorSpace,
                                                      double Limit);


LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreateLabProfile(LPcmsCIExyY WhitePoint);
LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreateLab4Profile(LPcmsCIExyY WhitePoint);

LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreateXYZProfile(void);
LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreate_sRGBProfile(void);



LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreateBCHSWabstractProfile(int nLUTPoints,
                                                     double Bright,
                                                     double Contrast,
                                                     double Hue,
                                                     double Saturation,
                                                     int TempSrc,
                                                     int TempDest);

LCMSAPI cmsHPROFILE   LCMSEXPORT cmsCreateNULLProfile(void);


// Colorimetric space conversions

LCMSAPI void          LCMSEXPORT cmsXYZ2xyY(LPcmsCIExyY Dest, const cmsCIEXYZ* Source);
LCMSAPI void          LCMSEXPORT cmsxyY2XYZ(LPcmsCIEXYZ Dest, const cmsCIExyY* Source);
LCMSAPI void          LCMSEXPORT cmsXYZ2Lab(LPcmsCIEXYZ WhitePoint, LPcmsCIELab Lab, const cmsCIEXYZ* xyz);
LCMSAPI void          LCMSEXPORT cmsLab2XYZ(LPcmsCIEXYZ WhitePoint, LPcmsCIEXYZ xyz, const cmsCIELab* Lab);
LCMSAPI void          LCMSEXPORT cmsLab2LCh(LPcmsCIELCh LCh, const cmsCIELab* Lab);
LCMSAPI void          LCMSEXPORT cmsLCh2Lab(LPcmsCIELab Lab, const cmsCIELCh* LCh);


// CIELab handling

LCMSAPI double        LCMSEXPORT cmsDeltaE(LPcmsCIELab Lab1, LPcmsCIELab Lab2);
LCMSAPI double        LCMSEXPORT cmsCIE94DeltaE(LPcmsCIELab Lab1, LPcmsCIELab Lab2);
LCMSAPI double        LCMSEXPORT cmsBFDdeltaE(LPcmsCIELab Lab1, LPcmsCIELab Lab2);
LCMSAPI double        LCMSEXPORT cmsCMCdeltaE(LPcmsCIELab Lab1, LPcmsCIELab Lab2);
LCMSAPI double        LCMSEXPORT cmsCIE2000DeltaE(LPcmsCIELab Lab1, LPcmsCIELab Lab2, double Kl, double Kc, double Kh);

LCMSAPI void          LCMSEXPORT cmsClampLab(LPcmsCIELab Lab, double amax, double amin, double bmax, double bmin);

LCMSAPI LCMSBOOL      LCMSEXPORT cmsWhitePointFromTemp(int TempK, LPcmsCIExyY WhitePoint);

LCMSAPI LCMSBOOL      LCMSEXPORT cmsAdaptToIlluminant(LPcmsCIEXYZ Result,
                                                        LPcmsCIEXYZ SourceWhitePt,
                                                        LPcmsCIEXYZ Illuminant,
                                                        LPcmsCIEXYZ Value);

LCMSAPI LCMSBOOL      LCMSEXPORT cmsBuildRGB2XYZtransferMatrix(LPMAT3 r,
                                                        LPcmsCIExyY WhitePoint,
                                                        LPcmsCIExyYTRIPLE Primaries);

// Viewing conditions

#define AVG_SURROUND_4     0
#define AVG_SURROUND       1
#define DIM_SURROUND       2
#define DARK_SURROUND      3
#define CUTSHEET_SURROUND  4

#define D_CALCULATE             (-1)
#define D_CALCULATE_DISCOUNT    (-2)

typedef struct {

              cmsCIEXYZ whitePoint;
              double    Yb;
              double    La;
              int       surround;
              double    D_value;

    } cmsViewingConditions;

typedef cmsViewingConditions FAR* LPcmsViewingConditions;

// CIECAM97s

LCMSAPI LCMSHANDLE    LCMSEXPORT cmsCIECAM97sInit(LPcmsViewingConditions pVC2);
LCMSAPI void          LCMSEXPORT cmsCIECAM97sDone(LCMSHANDLE hModel);
LCMSAPI void          LCMSEXPORT cmsCIECAM97sForward(LCMSHANDLE hModel, LPcmsCIEXYZ pIn, LPcmsJCh pOut);
LCMSAPI void          LCMSEXPORT cmsCIECAM97sReverse(LCMSHANDLE hModel, LPcmsJCh pIn,    LPcmsCIEXYZ pOut);


// CIECAM02

LCMSAPI LCMSHANDLE    LCMSEXPORT cmsCIECAM02Init(LPcmsViewingConditions pVC);
LCMSAPI void          LCMSEXPORT cmsCIECAM02Done(LCMSHANDLE hModel);
LCMSAPI void          LCMSEXPORT cmsCIECAM02Forward(LCMSHANDLE hModel, LPcmsCIEXYZ pIn, LPcmsJCh pOut);
LCMSAPI void          LCMSEXPORT cmsCIECAM02Reverse(LCMSHANDLE hModel, LPcmsJCh pIn,    LPcmsCIEXYZ pOut);


// Gamma

LCMSAPI LPGAMMATABLE  LCMSEXPORT cmsBuildGamma(int nEntries, double Gamma);
LCMSAPI LPGAMMATABLE  LCMSEXPORT cmsBuildParametricGamma(int nEntries, int Type, double Params[]);
LCMSAPI LPGAMMATABLE  LCMSEXPORT cmsAllocGamma(int nEntries);
LCMSAPI void          LCMSEXPORT cmsFreeGamma(LPGAMMATABLE Gamma);
LCMSAPI void          LCMSEXPORT cmsFreeGammaTriple(LPGAMMATABLE Gamma[3]);
LCMSAPI LPGAMMATABLE  LCMSEXPORT cmsDupGamma(LPGAMMATABLE Src);
LCMSAPI LPGAMMATABLE  LCMSEXPORT cmsReverseGamma(int nResultSamples, LPGAMMATABLE InGamma);
LCMSAPI LPGAMMATABLE  LCMSEXPORT cmsJoinGamma(LPGAMMATABLE InGamma,  LPGAMMATABLE OutGamma);
LCMSAPI LPGAMMATABLE  LCMSEXPORT cmsJoinGammaEx(LPGAMMATABLE InGamma,  LPGAMMATABLE OutGamma, int nPoints);
LCMSAPI LCMSBOOL      LCMSEXPORT cmsSmoothGamma(LPGAMMATABLE Tab, double lambda);
LCMSAPI double        LCMSEXPORT cmsEstimateGamma(LPGAMMATABLE t);
LCMSAPI double        LCMSEXPORT cmsEstimateGammaEx(LPWORD Table, int nEntries, double Thereshold);
LCMSAPI LPGAMMATABLE  LCMSEXPORT cmsReadICCGamma(cmsHPROFILE hProfile, icTagSignature sig);
LCMSAPI LPGAMMATABLE  LCMSEXPORT cmsReadICCGammaReversed(cmsHPROFILE hProfile, icTagSignature sig);

// Access to Profile data.

LCMSAPI LCMSBOOL      LCMSEXPORT cmsTakeMediaWhitePoint(LPcmsCIEXYZ Dest, cmsHPROFILE hProfile);
LCMSAPI LCMSBOOL      LCMSEXPORT cmsTakeMediaBlackPoint(LPcmsCIEXYZ Dest, cmsHPROFILE hProfile);
LCMSAPI LCMSBOOL      LCMSEXPORT cmsTakeIluminant(LPcmsCIEXYZ Dest, cmsHPROFILE hProfile);
LCMSAPI LCMSBOOL      LCMSEXPORT cmsTakeColorants(LPcmsCIEXYZTRIPLE Dest, cmsHPROFILE hProfile);
LCMSAPI DWORD         LCMSEXPORT cmsTakeHeaderFlags(cmsHPROFILE hProfile);
LCMSAPI DWORD         LCMSEXPORT cmsTakeHeaderAttributes(cmsHPROFILE hProfile);

LCMSAPI void          LCMSEXPORT cmsSetLanguage(const char LanguageCode[4], const char CountryCode[4]);
LCMSAPI const char*   LCMSEXPORT cmsTakeProductName(cmsHPROFILE hProfile);
LCMSAPI const char*   LCMSEXPORT cmsTakeProductDesc(cmsHPROFILE hProfile);
LCMSAPI const char*   LCMSEXPORT cmsTakeProductInfo(cmsHPROFILE hProfile);
LCMSAPI const char*   LCMSEXPORT cmsTakeManufacturer(cmsHPROFILE hProfile);
LCMSAPI const char*   LCMSEXPORT cmsTakeModel(cmsHPROFILE hProfile);
LCMSAPI const char*   LCMSEXPORT cmsTakeCopyright(cmsHPROFILE hProfile);
LCMSAPI const BYTE*   LCMSEXPORT cmsTakeProfileID(cmsHPROFILE hProfile);

LCMSAPI LCMSBOOL      LCMSEXPORT cmsTakeCreationDateTime(struct tm *Dest, cmsHPROFILE hProfile);
LCMSAPI LCMSBOOL      LCMSEXPORT cmsTakeCalibrationDateTime(struct tm *Dest, cmsHPROFILE hProfile);

LCMSAPI LCMSBOOL      LCMSEXPORT cmsIsTag(cmsHPROFILE hProfile, icTagSignature sig);
LCMSAPI int           LCMSEXPORT cmsTakeRenderingIntent(cmsHPROFILE hProfile);

LCMSAPI LCMSBOOL      LCMSEXPORT cmsTakeCharTargetData(cmsHPROFILE hProfile, char** Data, size_t* len);

LCMSAPI int           LCMSEXPORT cmsReadICCTextEx(cmsHPROFILE hProfile, icTagSignature sig, char *Text, size_t size);
LCMSAPI int           LCMSEXPORT cmsReadICCText(cmsHPROFILE hProfile, icTagSignature sig, char *Text);


#define LCMS_DESC_MAX     512

typedef struct {

            icSignature                 deviceMfg;
            icSignature                 deviceModel;
            icUInt32Number              attributes[2];
            icTechnologySignature       technology;

            char Manufacturer[LCMS_DESC_MAX];
            char Model[LCMS_DESC_MAX];

    } cmsPSEQDESC, FAR *LPcmsPSEQDESC;

typedef struct {

            int n;
            cmsPSEQDESC seq[1];

    } cmsSEQ, FAR *LPcmsSEQ;


LCMSAPI LPcmsSEQ      LCMSEXPORT cmsReadProfileSequenceDescription(cmsHPROFILE hProfile);
LCMSAPI void          LCMSEXPORT cmsFreeProfileSequenceDescription(LPcmsSEQ pseq);


// Translate form/to our notation to ICC
LCMSAPI icColorSpaceSignature LCMSEXPORT _cmsICCcolorSpace(int OurNotation);
LCMSAPI int                   LCMSEXPORT _cmsLCMScolorSpace(icColorSpaceSignature ProfileSpace);
LCMSAPI int                   LCMSEXPORT _cmsChannelsOf(icColorSpaceSignature ColorSpace);
LCMSAPI LCMSBOOL              LCMSEXPORT _cmsIsMatrixShaper(cmsHPROFILE hProfile);

// How profiles may be used
#define LCMS_USED_AS_INPUT      0
#define LCMS_USED_AS_OUTPUT     1
#define LCMS_USED_AS_PROOF      2

LCMSAPI LCMSBOOL                LCMSEXPORT cmsIsIntentSupported(cmsHPROFILE hProfile, int Intent, int UsedDirection);

LCMSAPI icColorSpaceSignature   LCMSEXPORT cmsGetPCS(cmsHPROFILE hProfile);
LCMSAPI icColorSpaceSignature   LCMSEXPORT cmsGetColorSpace(cmsHPROFILE hProfile);
LCMSAPI icProfileClassSignature LCMSEXPORT cmsGetDeviceClass(cmsHPROFILE hProfile);
LCMSAPI DWORD                   LCMSEXPORT cmsGetProfileICCversion(cmsHPROFILE hProfile);
LCMSAPI void                    LCMSEXPORT cmsSetProfileICCversion(cmsHPROFILE hProfile, DWORD Version);
LCMSAPI icInt32Number           LCMSEXPORT cmsGetTagCount(cmsHPROFILE hProfile);
LCMSAPI icTagSignature          LCMSEXPORT cmsGetTagSignature(cmsHPROFILE hProfile, icInt32Number n);


LCMSAPI void          LCMSEXPORT cmsSetDeviceClass(cmsHPROFILE hProfile, icProfileClassSignature sig);
LCMSAPI void          LCMSEXPORT cmsSetColorSpace(cmsHPROFILE hProfile, icColorSpaceSignature sig);
LCMSAPI void          LCMSEXPORT cmsSetPCS(cmsHPROFILE hProfile, icColorSpaceSignature pcs);
LCMSAPI void          LCMSEXPORT cmsSetRenderingIntent(cmsHPROFILE hProfile, int RenderingIntent);
LCMSAPI void          LCMSEXPORT cmsSetHeaderFlags(cmsHPROFILE hProfile, DWORD Flags);
LCMSAPI void          LCMSEXPORT cmsSetHeaderAttributes(cmsHPROFILE hProfile, DWORD Flags);
LCMSAPI void          LCMSEXPORT cmsSetProfileID(cmsHPROFILE hProfile, LPBYTE ProfileID);

// Intents

#define INTENT_PERCEPTUAL                 0
#define INTENT_RELATIVE_COLORIMETRIC      1
#define INTENT_SATURATION                 2
#define INTENT_ABSOLUTE_COLORIMETRIC      3

// Flags

#define cmsFLAGS_MATRIXINPUT              0x0001
#define cmsFLAGS_MATRIXOUTPUT             0x0002
#define cmsFLAGS_MATRIXONLY               (cmsFLAGS_MATRIXINPUT|cmsFLAGS_MATRIXOUTPUT)

#define cmsFLAGS_NOWHITEONWHITEFIXUP      0x0004    // Don't hot fix scum dot
#define cmsFLAGS_NOPRELINEARIZATION       0x0010    // Don't create prelinearization tables
                                                    // on precalculated transforms (internal use)

#define cmsFLAGS_GUESSDEVICECLASS         0x0020    // Guess device class (for transform2devicelink)

#define cmsFLAGS_NOTCACHE                 0x0040    // Inhibit 1-pixel cache

#define cmsFLAGS_NOTPRECALC               0x0100
#define cmsFLAGS_NULLTRANSFORM            0x0200    // Don't transform anyway
#define cmsFLAGS_HIGHRESPRECALC           0x0400    // Use more memory to give better accurancy
#define cmsFLAGS_LOWRESPRECALC            0x0800    // Use less memory to minimize resouces


#define cmsFLAGS_WHITEBLACKCOMPENSATION   0x2000
#define cmsFLAGS_BLACKPOINTCOMPENSATION   cmsFLAGS_WHITEBLACKCOMPENSATION

// Proofing flags

#define cmsFLAGS_GAMUTCHECK               0x1000    // Out of Gamut alarm
#define cmsFLAGS_SOFTPROOFING             0x4000    // Do softproofing

// Black preservation

#define cmsFLAGS_PRESERVEBLACK            0x8000

// CRD special

#define cmsFLAGS_NODEFAULTRESOURCEDEF     0x01000000

// Gridpoints

#define cmsFLAGS_GRIDPOINTS(n)           (((n) & 0xFF) << 16)


// Transforms

LCMSAPI cmsHTRANSFORM LCMSEXPORT cmsCreateTransform(cmsHPROFILE Input,
                                               DWORD InputFormat,
                                               cmsHPROFILE Output,
                                               DWORD OutputFormat,
                                               int Intent,
                                               DWORD dwFlags);

LCMSAPI cmsHTRANSFORM LCMSEXPORT cmsCreateProofingTransform(cmsHPROFILE Input,
                                               DWORD InputFormat,
                                               cmsHPROFILE Output,
                                               DWORD OutputFormat,
                                               cmsHPROFILE Proofing,
                                               int Intent,
                                               int ProofingIntent,
                                               DWORD dwFlags);

LCMSAPI cmsHTRANSFORM LCMSEXPORT cmsCreateMultiprofileTransform(cmsHPROFILE hProfiles[],
                                                                int nProfiles,
                                                                DWORD InputFormat,
                                                                DWORD OutputFormat,
                                                                int Intent,
                                                                DWORD dwFlags);

LCMSAPI void         LCMSEXPORT cmsDeleteTransform(cmsHTRANSFORM hTransform);

LCMSAPI void         LCMSEXPORT cmsDoTransform(cmsHTRANSFORM Transform,
                                                 LPVOID InputBuffer,
                                                 LPVOID OutputBuffer,
                                                 unsigned int Size);

LCMSAPI void         LCMSEXPORT cmsChangeBuffersFormat(cmsHTRANSFORM hTransform, DWORD InputFormat, DWORD dwOutputFormat);

LCMSAPI void         LCMSEXPORT cmsSetAlarmCodes(int r, int g, int b);
LCMSAPI void         LCMSEXPORT cmsGetAlarmCodes(int *r, int *g, int *b);


// Adaptation state for absolute colorimetric intent

LCMSAPI double       LCMSEXPORT cmsSetAdaptationState(double d);


// Primary preservation strategy

#define LCMS_PRESERVE_PURE_K    0
#define LCMS_PRESERVE_K_PLANE   1

LCMSAPI int LCMSEXPORT cmsSetCMYKPreservationStrategy(int n);

// Named color support
typedef struct {
                char Name[MAX_PATH];
                WORD PCS[3];
                WORD DeviceColorant[MAXCHANNELS];


        } cmsNAMEDCOLOR, FAR* LPcmsNAMEDCOLOR;

typedef struct {
                int nColors;
                int Allocated;
                int ColorantCount;
                char Prefix[33];
                char Suffix[33];

                cmsNAMEDCOLOR List[1];

        } cmsNAMEDCOLORLIST, FAR* LPcmsNAMEDCOLORLIST;

// Named color support

LCMSAPI int      LCMSEXPORT cmsNamedColorCount(cmsHTRANSFORM xform);
LCMSAPI LCMSBOOL LCMSEXPORT cmsNamedColorInfo(cmsHTRANSFORM xform, int nColor, char* Name, char* Prefix, char* Suffix);
LCMSAPI int      LCMSEXPORT cmsNamedColorIndex(cmsHTRANSFORM xform, const char* Name);

// Colorant tables

LCMSAPI LPcmsNAMEDCOLORLIST LCMSEXPORT cmsReadColorantTable(cmsHPROFILE hProfile, icTagSignature sig);

// Profile creation

LCMSAPI LCMSBOOL LCMSEXPORT cmsAddTag(cmsHPROFILE hProfile, icTagSignature sig, const void* data);

// Converts a transform to a devicelink profile
LCMSAPI cmsHPROFILE LCMSEXPORT cmsTransform2DeviceLink(cmsHTRANSFORM hTransform, DWORD dwFlags);

// Set the 'save as 8-bit' flag
LCMSAPI void LCMSEXPORT _cmsSetLUTdepth(cmsHPROFILE hProfile, int depth);


// Save profile
LCMSAPI LCMSBOOL LCMSEXPORT _cmsSaveProfile(cmsHPROFILE hProfile, const char* FileName);
LCMSAPI LCMSBOOL LCMSEXPORT _cmsSaveProfileToMem(cmsHPROFILE hProfile, void *MemPtr,
                                                                size_t* BytesNeeded);



// PostScript ColorRenderingDictionary and ColorSpaceArray

LCMSAPI DWORD LCMSEXPORT cmsGetPostScriptCSA(cmsHPROFILE hProfile, int Intent, LPVOID Buffer, DWORD dwBufferLen);
LCMSAPI DWORD LCMSEXPORT cmsGetPostScriptCRD(cmsHPROFILE hProfile, int Intent, LPVOID Buffer, DWORD dwBufferLen);
LCMSAPI DWORD LCMSEXPORT cmsGetPostScriptCRDEx(cmsHPROFILE hProfile, int Intent, DWORD dwFlags, LPVOID Buffer, DWORD dwBufferLen);


// Error handling

#define LCMS_ERROR_ABORT    0
#define LCMS_ERROR_SHOW     1
#define LCMS_ERROR_IGNORE   2

LCMSAPI int LCMSEXPORT cmsErrorAction(int nAction);

#define LCMS_ERRC_WARNING        0x1000
#define LCMS_ERRC_RECOVERABLE    0x2000
#define LCMS_ERRC_ABORTED        0x3000

typedef int (* cmsErrorHandlerFunction)(int ErrorCode, const char *ErrorText);

LCMSAPI void LCMSEXPORT cmsSetErrorHandler(cmsErrorHandlerFunction Fn);


// LUT manipulation


typedef struct _lcms_LUT_struc LUT, FAR* LPLUT; // opaque pointer

LCMSAPI LPLUT  LCMSEXPORT cmsAllocLUT(void);
LCMSAPI LPLUT  LCMSEXPORT cmsAllocLinearTable(LPLUT NewLUT, LPGAMMATABLE Tables[], int nTable);
LCMSAPI LPLUT  LCMSEXPORT cmsAlloc3DGrid(LPLUT Lut, int clutPoints, int inputChan, int outputChan);
LCMSAPI LPLUT  LCMSEXPORT cmsSetMatrixLUT(LPLUT Lut, LPMAT3 M);
LCMSAPI LPLUT  LCMSEXPORT cmsSetMatrixLUT4(LPLUT Lut, LPMAT3 M, LPVEC3 off, DWORD dwFlags);
LCMSAPI void   LCMSEXPORT cmsFreeLUT(LPLUT Lut);
LCMSAPI void   LCMSEXPORT cmsEvalLUT(LPLUT Lut, WORD In[], WORD Out[]);
LCMSAPI double LCMSEXPORT cmsEvalLUTreverse(LPLUT Lut, WORD Target[], WORD Result[], LPWORD Hint);
LCMSAPI LPLUT  LCMSEXPORT cmsReadICCLut(cmsHPROFILE hProfile, icTagSignature sig);
LCMSAPI LPLUT  LCMSEXPORT cmsDupLUT(LPLUT Orig);


// LUT Sampling

typedef int (* _cmsSAMPLER)(register WORD In[],
                            register WORD Out[],
                            register LPVOID Cargo);

#define SAMPLER_HASTL1      LUT_HASTL1
#define SAMPLER_HASTL2      LUT_HASTL2
#define SAMPLER_INSPECT     0x01000000

LCMSAPI int LCMSEXPORT cmsSample3DGrid(LPLUT Lut, _cmsSAMPLER Sampler, LPVOID Cargo, DWORD dwFlags);

// Formatters

typedef unsigned char* (* cmsFORMATTER)(register void* CMMcargo,
                                        register WORD ToUnroll[],
                                        register LPBYTE Buffer);

LCMSAPI void LCMSEXPORT cmsSetUserFormatters(cmsHTRANSFORM hTransform, DWORD dwInput,  cmsFORMATTER Input,
                                                               DWORD dwOutput, cmsFORMATTER Output);

LCMSAPI void LCMSEXPORT cmsGetUserFormatters(cmsHTRANSFORM hTransform,
                                                               LPDWORD InputFormat, cmsFORMATTER* Input,
                                                               LPDWORD OutputFormat, cmsFORMATTER* Output);


// IT8.7 / CGATS.17-200x handling

LCMSAPI LCMSHANDLE      LCMSEXPORT cmsIT8Alloc(void);
LCMSAPI void            LCMSEXPORT cmsIT8Free(LCMSHANDLE IT8);

// Tables

LCMSAPI int             LCMSEXPORT cmsIT8TableCount(LCMSHANDLE IT8);
LCMSAPI int             LCMSEXPORT cmsIT8SetTable(LCMSHANDLE IT8, int nTable);

// Persistence
LCMSAPI LCMSHANDLE      LCMSEXPORT cmsIT8LoadFromFile(const char* cFileName);
LCMSAPI LCMSHANDLE      LCMSEXPORT cmsIT8LoadFromMem(void *Ptr, size_t len);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SaveToFile(LCMSHANDLE IT8, const char* cFileName);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SaveToMem(LCMSHANDLE hIT8, void *MemPtr, size_t* BytesNeeded);

// Properties
LCMSAPI const char*     LCMSEXPORT cmsIT8GetSheetType(LCMSHANDLE hIT8);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetSheetType(LCMSHANDLE hIT8, const char* Type);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetComment(LCMSHANDLE hIT8, const char* cComment);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyStr(LCMSHANDLE hIT8, const char* cProp, const char *Str);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyDbl(LCMSHANDLE hIT8, const char* cProp, double Val);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyHex(LCMSHANDLE hIT8, const char* cProp, int Val);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyMulti(LCMSHANDLE hIT8, const char* cProp, const char* cSubProp, const char *Val);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetPropertyUncooked(LCMSHANDLE hIT8, const char* Key, const char* Buffer);


LCMSAPI const char*     LCMSEXPORT cmsIT8GetProperty(LCMSHANDLE hIT8, const char* cProp);
LCMSAPI double          LCMSEXPORT cmsIT8GetPropertyDbl(LCMSHANDLE hIT8, const char* cProp);
LCMSAPI const char*     LCMSEXPORT cmsIT8GetPropertyMulti(LCMSHANDLE hIT8, const char* cProp, const char *cSubProp);
LCMSAPI int             LCMSEXPORT cmsIT8EnumProperties(LCMSHANDLE hIT8, const char ***PropertyNames);
LCMSAPI int             LCMSEXPORT cmsIT8EnumPropertyMulti(LCMSHANDLE hIT8, const char* cProp, const char*** SubpropertyNames);

// Datasets

LCMSAPI const char*     LCMSEXPORT cmsIT8GetDataRowCol(LCMSHANDLE IT8, int row, int col);
LCMSAPI double          LCMSEXPORT cmsIT8GetDataRowColDbl(LCMSHANDLE IT8, int row, int col);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetDataRowCol(LCMSHANDLE hIT8, int row, int col,
                                                const char* Val);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetDataRowColDbl(LCMSHANDLE hIT8, int row, int col,
                                                double Val);

LCMSAPI const char*     LCMSEXPORT cmsIT8GetData(LCMSHANDLE IT8, const char* cPatch, const char* cSample);


LCMSAPI double          LCMSEXPORT cmsIT8GetDataDbl(LCMSHANDLE IT8, const char* cPatch, const char* cSample);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetData(LCMSHANDLE IT8, const char* cPatch,
                                                const char* cSample,
                                                const char *Val);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetDataDbl(LCMSHANDLE hIT8, const char* cPatch,
                                                const char* cSample,
                                                double Val);

LCMSAPI int             LCMSEXPORT cmsIT8GetDataFormat(LCMSHANDLE hIT8, const char* cSample);
LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetDataFormat(LCMSHANDLE IT8, int n, const char *Sample);
LCMSAPI int             LCMSEXPORT cmsIT8EnumDataFormat(LCMSHANDLE IT8, char ***SampleNames);


LCMSAPI const char*     LCMSEXPORT cmsIT8GetPatchName(LCMSHANDLE hIT8, int nPatch, char* buffer);
LCMSAPI int             LCMSEXPORT cmsIT8GetPatchByName(LCMSHANDLE hIT8, const char *cSample);

// The LABEL extension

LCMSAPI int             LCMSEXPORT cmsIT8SetTableByLabel(LCMSHANDLE hIT8, const char* cSet, const char* cField, const char* ExpectedType);

LCMSAPI LCMSBOOL        LCMSEXPORT cmsIT8SetIndexColumn(LCMSHANDLE hIT8, const char* cSample);

// Formatter for double
LCMSAPI void            LCMSEXPORT cmsIT8DefineDblFormat(LCMSHANDLE IT8, const char* Formatter);


// ***************************************************************************
// End of Little cms API From here functions are private
// You can use them only if using static libraries, and at your own risk of
// be stripped or changed at futures releases.

#ifndef LCMS_APIONLY


// Compatibility with anterior versions-- not needed anymore
//  -- Morge

LCMSAPI void          LCMSEXPORT cmsLabEncoded2Float(LPcmsCIELab Lab, const WORD wLab[3]);
LCMSAPI void          LCMSEXPORT cmsLabEncoded2Float4(LPcmsCIELab Lab, const WORD wLab[3]);
LCMSAPI void          LCMSEXPORT cmsFloat2LabEncoded(WORD wLab[3], const cmsCIELab* Lab);
LCMSAPI void          LCMSEXPORT cmsFloat2LabEncoded4(WORD wLab[3], const cmsCIELab* Lab);
LCMSAPI void          LCMSEXPORT cmsXYZEncoded2Float(LPcmsCIEXYZ fxyz, const WORD XYZ[3]);
LCMSAPI void          LCMSEXPORT cmsFloat2XYZEncoded(WORD XYZ[3], const cmsCIEXYZ* fXYZ);


// Profiling Extensions --- Would be removed from API in future revisions

LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddTextTag(cmsHPROFILE hProfile,  icTagSignature sig, const char* Text);
LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddXYZTag(cmsHPROFILE hProfile,   icTagSignature sig, const cmsCIEXYZ* XYZ);
LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddLUTTag(cmsHPROFILE hProfile,   icTagSignature sig, const void* lut);
LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddGammaTag(cmsHPROFILE hProfile, icTagSignature sig, LPGAMMATABLE TransferFunction);
LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddChromaticityTag(cmsHPROFILE hProfile, icTagSignature sig, LPcmsCIExyYTRIPLE Chrm);
LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddSequenceDescriptionTag(cmsHPROFILE hProfile, icTagSignature sig, LPcmsSEQ PSeq);
LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddNamedColorTag(cmsHPROFILE hProfile, icTagSignature sig, LPcmsNAMEDCOLORLIST nc);
LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddDateTimeTag(cmsHPROFILE hProfile, icTagSignature sig, struct tm *DateTime);
LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddColorantTableTag(cmsHPROFILE hProfile, icTagSignature sig, LPcmsNAMEDCOLORLIST nc);
LCMSAPI LCMSBOOL      LCMSEXPORT _cmsAddChromaticAdaptationTag(cmsHPROFILE hProfile, icTagSignature sig, const cmsCIEXYZ* mat);

// --------------------------------------------------------------------------------------------------- Inline functions

// Fast floor conversion logic. Thanks to Sree Kotay and Stuart Nixon
// note than this only works in the range ..-32767...+32767 because
// mantissa is interpreted as 15.16 fixed point.
// The union is to avoid pointer aliasing overoptimization.

LCMS_INLINE int _cmsQuickFloor(double val)
{
#ifdef USE_DEFAULT_FLOOR_CONVERSION
    return (int) floor(val);
#else
    const double _lcms_double2fixmagic = 68719476736.0 * 1.5;  // 2^36 * 1.5, (52-16=36) uses limited precision to floor
    union {
        double val;
        int halves[2];
    } temp;

    temp.val = val + _lcms_double2fixmagic;


#ifdef USE_BIG_ENDIAN
    return temp.halves[1] >> 16;
#else
    return temp.halves[0] >> 16;
#endif
#endif
}



// Clamp with saturation

LCMS_INLINE WORD _cmsClampWord(int in)
{
       if (in < 0) return 0;
       if (in > 0xFFFF) return 0xFFFFU;   // Including marker
       return (WORD) in;
}

#ifndef LCMS_USER_ALLOC

// Low-level alloc hook

LCMS_INLINE void* _cmsMalloc(size_t size)
{
    if (size > ((size_t) 1024*1024*500)) return NULL;  // Never allow over 500Mb
    if (size < 0) return NULL;              // Prevent signed size_t exploits

    return (void*) malloc(size);
}

LCMS_INLINE void* _cmsCalloc(size_t nmemb, size_t size)
{
    size_t alloc = nmemb * size;

        if (size == 0) {
                return _cmsMalloc(0);
        }
        if (alloc / size != nmemb) {
        return NULL;
    }
    return _cmsMalloc(alloc);
}

LCMS_INLINE void _cmsFree(void *Ptr)
{
    if (Ptr) free(Ptr);
}

#endif

// ------------------------------------------------------------------------------------------- end of inline functions

// Signal error from inside lcms code

void cdecl cmsSignalError(int ErrorCode, const char *ErrorText, ...);

// Alignment handling (needed in ReadLUT16 and ReadLUT8)

typedef struct {
        icS15Fixed16Number a;
        icUInt16Number     b;

       } _cmsTestAlign16;

#define SIZEOF_UINT16_ALIGNED (sizeof(_cmsTestAlign16) - sizeof(icS15Fixed16Number))

typedef struct {
        icS15Fixed16Number a;
        icUInt8Number      b;

       } _cmsTestAlign8;

#define SIZEOF_UINT8_ALIGNED (sizeof(_cmsTestAlign8) - sizeof(icS15Fixed16Number))


// Fixed point


typedef icInt32Number Fixed32;       // Fixed 15.16 whith sign

#define INT_TO_FIXED(x)         ((x)<<16)
#define DOUBLE_TO_FIXED(x)      ((Fixed32) ((x)*65536.0+0.5))
#define FIXED_TO_INT(x)         ((x)>>16)
#define FIXED_REST_TO_INT(x)    ((x)& 0xFFFFU)
#define FIXED_TO_DOUBLE(x)      (((double)x)/65536.0)
#define ROUND_FIXED_TO_INT(x)   (((x)+0x8000)>>16)


Fixed32 cdecl FixedMul(Fixed32 a, Fixed32 b);
Fixed32 cdecl FixedSquare(Fixed32 a);


#ifdef USE_INLINE

LCMS_INLINE Fixed32 ToFixedDomain(int a)        { return a + ((a + 0x7fff) / 0xffff); }
LCMS_INLINE int     FromFixedDomain(Fixed32 a)  { return a - ((a + 0x7fff) >> 16); }

#else

Fixed32 cdecl ToFixedDomain(int a);              // (a * 65536.0 / 65535.0)
int     cdecl FromFixedDomain(Fixed32 a);        // (a * 65535.0 + .5)

#endif

Fixed32 cdecl FixedLERP(Fixed32 a, Fixed32 l, Fixed32 h);
WORD    cdecl FixedScale(WORD a, Fixed32 s);

// Vector & Matrix operations. I'm using the notation frequently found in
// literature. Mostly 'Graphic Gems' samples. Not to be same routines.

// Vector members

#define VX      0
#define VY      1
#define VZ      2

typedef struct {                // Fixed 15.16 bits vector
        Fixed32 n[3];
        } WVEC3, FAR* LPWVEC3;

typedef struct {                // Matrix (Fixed 15.16)
        WVEC3 v[3];
        } WMAT3, FAR* LPWMAT3;



void      cdecl VEC3init(LPVEC3 r, double x, double y, double z);   // double version
void      cdecl VEC3initF(LPWVEC3 r, double x, double y, double z); // Fix32 version
void      cdecl VEC3toFix(LPWVEC3 r, LPVEC3 v);
void      cdecl VEC3fromFix(LPVEC3 r, LPWVEC3 v);
void      cdecl VEC3scaleFix(LPWORD r, LPWVEC3 Scale);
void      cdecl VEC3swap(LPVEC3 a, LPVEC3 b);
void      cdecl VEC3divK(LPVEC3 r, LPVEC3 v, double d);
void      cdecl VEC3perK(LPVEC3 r, LPVEC3 v, double d);
void      cdecl VEC3minus(LPVEC3 r, LPVEC3 a, LPVEC3 b);
void      cdecl VEC3perComp(LPVEC3 r, LPVEC3 a, LPVEC3 b);
LCMSBOOL  cdecl VEC3equal(LPWVEC3 a, LPWVEC3 b, double Tolerance);
LCMSBOOL  cdecl VEC3equalF(LPVEC3 a, LPVEC3 b, double Tolerance);
void      cdecl VEC3scaleAndCut(LPWVEC3 r, LPVEC3 v, double d);
void      cdecl VEC3cross(LPVEC3 r, LPVEC3 u, LPVEC3 v);
void      cdecl VEC3saturate(LPVEC3 v);
double    cdecl VEC3distance(LPVEC3 a, LPVEC3 b);
double    cdecl VEC3length(LPVEC3 a);

void      cdecl MAT3identity(LPMAT3 a);
void      cdecl MAT3per(LPMAT3 r, LPMAT3 a, LPMAT3 b);
void      cdecl MAT3perK(LPMAT3 r, LPMAT3 v, double d);
int       cdecl MAT3inverse(LPMAT3 a, LPMAT3 b);
LCMSBOOL  cdecl MAT3solve(LPVEC3 x, LPMAT3 a, LPVEC3 b);
double    cdecl MAT3det(LPMAT3 m);
void      cdecl MAT3eval(LPVEC3 r, LPMAT3 a, LPVEC3 v);
void      cdecl MAT3toFix(LPWMAT3 r, LPMAT3 v);
void      cdecl MAT3fromFix(LPMAT3 r, LPWMAT3 v);
void      cdecl MAT3evalW(LPWVEC3 r, LPWMAT3 a, LPWVEC3 v);
LCMSBOOL  cdecl MAT3isIdentity(LPWMAT3 a, double Tolerance);
void      cdecl MAT3scaleAndCut(LPWMAT3 r, LPMAT3 v, double d);

// Is a table linear?

int  cdecl cmsIsLinear(WORD Table[], int nEntries);

// I hold this structures describing domain
// details mainly for optimization purposes.

struct _lcms_l16params_struc;

typedef void (* _cms3DLERP)(WORD Input[],
                            WORD Output[],
                            WORD LutTable[],
                            struct _lcms_l16params_struc* p);



typedef struct _lcms_l8opt_struc {      // Used on 8 bit interpolations

              unsigned int X0[256], Y0[256], Z0[256];
              WORD rx[256], ry[256], rz[256];

        } L8PARAMS, FAR* LPL8PARAMS;

typedef struct _lcms_l16params_struc {    // Used on 16 bits interpolations

               int nSamples;       // Valid on all kinds of tables
               int nInputs;        // != 1 only in 3D interpolation
               int nOutputs;       // != 1 only in 3D interpolation

               WORD Domain;

               int opta1, opta2;
               int opta3, opta4;     // Optimization for 3D LUT
               int opta5, opta6;
               int opta7, opta8;

               _cms3DLERP Interp3D; // The interpolation routine

                LPL8PARAMS p8;      // Points to some tables for 8-bit speedup

               } L16PARAMS, *LPL16PARAMS;


void    cdecl cmsCalcL16Params(int nSamples, LPL16PARAMS p);
void    cdecl cmsCalcCLUT16Params(int nSamples, int InputChan, int OutputChan, LPL16PARAMS p);
void    cdecl cmsCalcCLUT16ParamsEx(int nSamples, int InputChan, int OutputChan,
                                            LCMSBOOL lUseTetrahedral, LPL16PARAMS p);

WORD    cdecl cmsLinearInterpLUT16(WORD Value, WORD LutTable[], LPL16PARAMS p);
Fixed32 cdecl cmsLinearInterpFixed(WORD Value1, WORD LutTable[], LPL16PARAMS p);
WORD    cdecl cmsReverseLinearInterpLUT16(WORD Value, WORD LutTable[], LPL16PARAMS p);

void cdecl cmsTrilinearInterp16(WORD Input[],
                                WORD Output[],
                                WORD LutTable[],
                                LPL16PARAMS p);

void cdecl cmsTetrahedralInterp16(WORD Input[],
                                  WORD Output[],
                                  WORD LutTable[], LPL16PARAMS p);

void cdecl cmsTetrahedralInterp8(WORD Input[],
                                 WORD Output[],
                                 WORD LutTable[],  LPL16PARAMS p);

// LUT handling

#define LUT_HASMATRIX       0x0001        // Do-op Flags
#define LUT_HASTL1          0x0002
#define LUT_HASTL2          0x0008
#define LUT_HAS3DGRID       0x0010

// New in rev 4.0 of ICC spec

#define LUT_HASMATRIX3     0x0020   // Matrix + offset for LutAToB
#define LUT_HASMATRIX4     0x0040   // Matrix + offset for LutBToA

#define LUT_HASTL3         0x0100   // '3' curves for LutAToB
#define LUT_HASTL4         0x0200   // '4' curves for LutBToA

// V4 emulation

#define LUT_V4_OUTPUT_EMULATE_V2    0x10000     // Is a V4 output LUT, emulating V2
#define LUT_V4_INPUT_EMULATE_V2     0x20000     // Is a V4 input LUT, emulating V2
#define LUT_V2_OUTPUT_EMULATE_V4    0x40000     // Is a V2 output LUT, emulating V4
#define LUT_V2_INPUT_EMULATE_V4     0x80000     // Is a V2 input LUT, emulating V4


struct _lcms_LUT_struc {

               DWORD wFlags;
               WMAT3 Matrix;                    // 15fixed16 matrix

               unsigned int InputChan;
               unsigned int OutputChan;
               unsigned int InputEntries;
               unsigned int OutputEntries;
               unsigned int cLutPoints;


               LPWORD L1[MAXCHANNELS];          // First linearization
               LPWORD L2[MAXCHANNELS];          // Last linearization

               LPWORD T;                        // 3D CLUT
               unsigned int Tsize;              // CLUT size in bytes

              // Parameters & Optimizations

               L16PARAMS In16params;
               L16PARAMS Out16params;
               L16PARAMS CLut16params;

               int Intent;                       // Accomplished intent

               // New for Rev 4.0 of spec (reserved)

               WMAT3 Mat3;
               WVEC3 Ofs3;
               LPWORD L3[MAXCHANNELS];
               L16PARAMS L3params;
               unsigned int L3Entries;

               WMAT3 Mat4;
               WVEC3 Ofs4;
               LPWORD L4[MAXCHANNELS];
               L16PARAMS L4params;
               unsigned int L4Entries;

               // Gray axes fixup. Only on v2 8-bit Lab LUT

               LCMSBOOL FixGrayAxes;


               // Parameters used for curve creation

               LCMSGAMMAPARAMS LCurvesSeed[4][MAXCHANNELS];


               }; // LUT, FAR* LPLUT;


LCMSBOOL         cdecl _cmsSmoothEndpoints(LPWORD Table, int nEntries);


// CRC of gamma tables

unsigned int _cmsCrc32OfGammaTable(LPGAMMATABLE Table);

// Sampled curves

LPSAMPLEDCURVE cdecl cmsAllocSampledCurve(int nItems);
void           cdecl cmsFreeSampledCurve(LPSAMPLEDCURVE p);
LPSAMPLEDCURVE cdecl cmsDupSampledCurve(LPSAMPLEDCURVE p);

LPSAMPLEDCURVE cdecl cmsConvertGammaToSampledCurve(LPGAMMATABLE Gamma, int nPoints);
LPGAMMATABLE   cdecl cmsConvertSampledCurveToGamma(LPSAMPLEDCURVE Sampled, double Max);

void           cdecl cmsEndpointsOfSampledCurve(LPSAMPLEDCURVE p, double* Min, double* Max);
void           cdecl cmsClampSampledCurve(LPSAMPLEDCURVE p, double Min, double Max);
LCMSBOOL       cdecl cmsSmoothSampledCurve(LPSAMPLEDCURVE Tab, double SmoothingLambda);
void           cdecl cmsRescaleSampledCurve(LPSAMPLEDCURVE p, double Min, double Max, int nPoints);

LPSAMPLEDCURVE cdecl cmsJoinSampledCurves(LPSAMPLEDCURVE X, LPSAMPLEDCURVE Y, int nResultingPoints);

// Shaper/Matrix handling

#define MATSHAPER_HASMATRIX        0x0001        // Do-ops flags
#define MATSHAPER_HASSHAPER        0x0002
#define MATSHAPER_INPUT            0x0004        // Behaviour
#define MATSHAPER_OUTPUT           0x0008
#define MATSHAPER_HASINPSHAPER     0x0010
#define MATSHAPER_ALLSMELTED       (MATSHAPER_INPUT|MATSHAPER_OUTPUT)


typedef struct {
               DWORD dwFlags;

               WMAT3 Matrix;

               L16PARAMS p16;       // Primary curve
               LPWORD L[3];

               L16PARAMS p2_16;     // Secondary curve (used as input in smelted ones)
               LPWORD L2[3];

               } MATSHAPER, FAR* LPMATSHAPER;

LPMATSHAPER cdecl cmsAllocMatShaper(LPMAT3 matrix, LPGAMMATABLE Shaper[], DWORD Behaviour);
LPMATSHAPER cdecl cmsAllocMatShaper2(LPMAT3 matrix, LPGAMMATABLE In[], LPGAMMATABLE Out[], DWORD Behaviour);

void        cdecl cmsFreeMatShaper(LPMATSHAPER MatShaper);
void        cdecl cmsEvalMatShaper(LPMATSHAPER MatShaper, WORD In[], WORD Out[]);

LCMSBOOL    cdecl cmsReadICCMatrixRGB2XYZ(LPMAT3 r, cmsHPROFILE hProfile);

LPMATSHAPER cdecl cmsBuildInputMatrixShaper(cmsHPROFILE InputProfile);
LPMATSHAPER cdecl cmsBuildOutputMatrixShaper(cmsHPROFILE OutputProfile);



// White Point & Primary chromas handling
LCMSBOOL cdecl cmsAdaptationMatrix(LPMAT3 r, LPMAT3 ConeMatrix, LPcmsCIEXYZ FromIll, LPcmsCIEXYZ ToIll);
LCMSBOOL cdecl cmsAdaptMatrixToD50(LPMAT3 r, LPcmsCIExyY SourceWhitePt);
LCMSBOOL cdecl cmsAdaptMatrixFromD50(LPMAT3 r, LPcmsCIExyY DestWhitePt);

LCMSBOOL cdecl cmsReadChromaticAdaptationMatrix(LPMAT3 r, cmsHPROFILE hProfile);

// Inter-PCS conversion routines. They assume D50 as white point.
void cdecl cmsXYZ2LabEncoded(WORD XYZ[3], WORD Lab[3]);
void cdecl cmsLab2XYZEncoded(WORD Lab[3], WORD XYZ[3]);

// Retrieve text representation of WP
void cdecl _cmsIdentifyWhitePoint(char *Buffer, LPcmsCIEXYZ WhitePt);

// Quantize to WORD in a (MaxSamples - 1) domain
WORD cdecl _cmsQuantizeVal(double i, int MaxSamples);

LPcmsNAMEDCOLORLIST  cdecl cmsAllocNamedColorList(int n);
int                  cdecl cmsReadICCnamedColorList(cmsHTRANSFORM xform, cmsHPROFILE hProfile, icTagSignature sig);
void                 cdecl cmsFreeNamedColorList(LPcmsNAMEDCOLORLIST List);
LCMSBOOL             cdecl cmsAppendNamedColor(cmsHTRANSFORM xform, const char* Name, WORD PCS[3], WORD Colorant[MAXCHANNELS]);


// I/O

#define MAX_TABLE_TAG       100

// This is the internal struct holding profile details.

typedef struct _lcms_iccprofile_struct {

              void* stream;   // Associated stream. If NULL,
                              // tags are supposed to be in
                              // memory rather than in a file.

               // Only most important items found in ICC profile

               icProfileClassSignature DeviceClass;
               icColorSpaceSignature   ColorSpace;
               icColorSpaceSignature   PCS;
               icRenderingIntent       RenderingIntent;
               icUInt32Number          flags;
               icUInt32Number          attributes;
               cmsCIEXYZ               Illuminant;

               // Additions for V4 profiles

               icUInt32Number          Version;
               MAT3                    ChromaticAdaptation;
               cmsCIEXYZ               MediaWhitePoint;
               cmsCIEXYZ               MediaBlackPoint;
               BYTE                    ProfileID[16];


               // Dictionary

               icInt32Number           TagCount;
               icTagSignature          TagNames[MAX_TABLE_TAG];
               size_t                  TagSizes[MAX_TABLE_TAG];
               size_t                  TagOffsets[MAX_TABLE_TAG];
               LPVOID                  TagPtrs[MAX_TABLE_TAG];

               char                    PhysicalFile[MAX_PATH];

               LCMSBOOL                IsWrite;
               LCMSBOOL                SaveAs8Bits;

               struct tm               Created;

               // I/O handlers

               size_t   (* Read)(void *buffer, size_t size, size_t count, struct _lcms_iccprofile_struct* Icc);

               LCMSBOOL (* Seek)(struct _lcms_iccprofile_struct* Icc, size_t offset);
               LCMSBOOL (* Close)(struct _lcms_iccprofile_struct* Icc);
               size_t   (* Tell)(struct _lcms_iccprofile_struct* Icc);
               LCMSBOOL  (* Grow)(struct _lcms_iccprofile_struct* Icc, size_t amount);

               // Writting

               LCMSBOOL (* Write)(struct _lcms_iccprofile_struct* Icc, size_t size, LPVOID Ptr);

               size_t UsedSpace;


              } LCMSICCPROFILE, FAR* LPLCMSICCPROFILE;


// Create an empty template for virtual profiles
cmsHPROFILE cdecl _cmsCreateProfilePlaceholder(void);

// Search into tag dictionary
icInt32Number cdecl _cmsSearchTag(LPLCMSICCPROFILE Profile, icTagSignature sig, LCMSBOOL lSignalError);

// Search for a particular tag, replace if found or add new one else
LPVOID _cmsInitTag(LPLCMSICCPROFILE Icc, icTagSignature sig, size_t size, const void* Init);


LPLCMSICCPROFILE cdecl _cmsCreateProfileFromFilePlaceholder(const char* FileName);
LPLCMSICCPROFILE cdecl _cmsCreateProfileFromMemPlaceholder(LPVOID MemPtr, DWORD dwSize);

void _cmsSetSaveToDisk(LPLCMSICCPROFILE Icc, const char* FileName);
void _cmsSetSaveToMemory(LPLCMSICCPROFILE Icc, LPVOID MemPtr, size_t dwSize);



// These macros unpack format specifiers into integers

#define T_DITHER(s)           (((s)>>22)&1)
#define T_COLORSPACE(s)       (((s)>>16)&31)
#define T_SWAPFIRST(s)        (((s)>>14)&1)
#define T_FLAVOR(s)           (((s)>>13)&1)
#define T_PLANAR(p)           (((p)>>12)&1)
#define T_ENDIAN16(e)         (((e)>>11)&1)
#define T_DOSWAP(e)           (((e)>>10)&1)
#define T_EXTRA(e)            (((e)>>7)&7)
#define T_CHANNELS(c)         (((c)>>3)&15)
#define T_BYTES(b)            ((b)&7)



// Internal XFORM struct
struct _cmstransform_struct;

// Full xform
typedef void (* _cmsCOLORCALLBACKFN)(struct _cmstransform_struct *Transform,
                               LPVOID InputBuffer,
                               LPVOID OutputBuffer, unsigned int Size);

// intermediate pass, from WORD[] to WORD[]

typedef void   (* _cmsADJFN)(WORD In[], WORD Out[], LPWMAT3 m, LPWVEC3 b);

typedef void   (* _cmsTRANSFN)(struct _cmstransform_struct *Transform,
                               WORD In[], WORD Out[]);

typedef void   (* _cmsCNVRT)(WORD In[], WORD Out[]);

typedef LPBYTE (* _cmsFIXFN)(register struct _cmstransform_struct *info,
                             register WORD ToUnroll[],
                             register LPBYTE Buffer);



// Transformation
typedef struct _cmstransform_struct {

                    // Keep formats for further reference
                    DWORD InputFormat, OutputFormat;

                    DWORD StrideIn, StrideOut;      // Planar support

                    int Intent, ProofIntent;
                    int DoGamutCheck;


                    cmsHPROFILE InputProfile;
                    cmsHPROFILE OutputProfile;
                    cmsHPROFILE PreviewProfile;

                    icColorSpaceSignature EntryColorSpace;
                    icColorSpaceSignature ExitColorSpace;

                    DWORD dwOriginalFlags;      // Flags as specified by user

                    WMAT3 m1, m2;       // Matrix holding inter PCS operation
                    WVEC3 of1, of2;     // Offset terms

                    _cmsCOLORCALLBACKFN xform;

                    // Steps in xFORM

                    _cmsFIXFN   FromInput;
                    _cmsTRANSFN FromDevice;
                    _cmsADJFN   Stage1;
                    _cmsADJFN   Stage2;
                    _cmsTRANSFN ToDevice;
                    _cmsFIXFN   ToOutput;

                    // LUTs

                    LPLUT Device2PCS;
                    LPLUT PCS2Device;
                    LPLUT Gamut;         // Gamut check
                    LPLUT Preview;       // Preview (Proof)

                    LPLUT DeviceLink;    // Precalculated grid - device link profile
                    LPLUT GamutCheck;    // Precalculated device -> gamut check

                    // Matrix/Shapers

                    LPMATSHAPER InMatShaper;
                    LPMATSHAPER OutMatShaper;
                    LPMATSHAPER SmeltMatShaper;

                    // Phase of Lab/XYZ, Abs/Rel

                    int Phase1, Phase2, Phase3;

                    // Named color table

                    LPcmsNAMEDCOLORLIST NamedColorList;

                    // Flag for transform involving v4 profiles

                    LCMSBOOL lInputV4Lab, lOutputV4Lab;


                    // 1-pixel cache

                    WORD CacheIn[MAXCHANNELS];
                    WORD CacheOut[MAXCHANNELS];

                    double AdaptationState; // Figure for v4 incomplete state of adaptation

                    LCMS_RWLOCK_T rwlock;

                   } _cmsTRANSFORM,FAR *_LPcmsTRANSFORM;



// Packing & Unpacking

_cmsFIXFN cdecl _cmsIdentifyInputFormat(_LPcmsTRANSFORM xform,  DWORD dwInput);
_cmsFIXFN cdecl _cmsIdentifyOutputFormat(_LPcmsTRANSFORM xform, DWORD dwOutput);


// Conversion

#define XYZRel       0
#define LabRel       1


int cdecl cmsChooseCnvrt(int Absolute,
                 int Phase1, LPcmsCIEXYZ BlackPointIn,
                             LPcmsCIEXYZ WhitePointIn,
                             LPcmsCIEXYZ IlluminantIn,
                             LPMAT3 ChromaticAdaptationMatrixIn,

                 int Phase2, LPcmsCIEXYZ BlackPointOut,
                             LPcmsCIEXYZ WhitePointOut,
                             LPcmsCIEXYZ IlluminantOut,
                             LPMAT3 ChromaticAdaptationMatrixOut,
                 int DoBPC,
                 double AdaptationState,
                 _cmsADJFN *fn1,
                 LPWMAT3 wm, LPWVEC3 wof);



// Clamping & Gamut handling

LCMSBOOL cdecl   _cmsEndPointsBySpace(icColorSpaceSignature Space,
                            WORD **White, WORD **Black, int *nOutputs);

WORD * cdecl _cmsWhiteBySpace(icColorSpaceSignature Space);



WORD cdecl Clamp_L(Fixed32 in);
WORD cdecl Clamp_ab(Fixed32 in);

// Detection of black point

#define LCMS_BPFLAGS_D50_ADAPTED    0x0001

int cdecl cmsDetectBlackPoint(LPcmsCIEXYZ BlackPoint, cmsHPROFILE hProfile, int Intent, DWORD dwFlags);

// choose reasonable resolution
int cdecl _cmsReasonableGridpointsByColorspace(icColorSpaceSignature Colorspace, DWORD dwFlags);

// Precalculate device link
LPLUT cdecl _cmsPrecalculateDeviceLink(cmsHTRANSFORM h, DWORD dwFlags);

// Precalculate black preserving device link
LPLUT _cmsPrecalculateBlackPreservingDeviceLink(cmsHTRANSFORM hCMYK2CMYK, DWORD dwFlags);

// Precalculate gamut check
LPLUT cdecl _cmsPrecalculateGamutCheck(cmsHTRANSFORM h);

// Hot fixes bad profiles
LCMSBOOL cdecl _cmsFixWhiteMisalignment(_LPcmsTRANSFORM p);

// Marks LUT as 8 bit on input
LPLUT cdecl _cmsBlessLUT8(LPLUT Lut);

// Compute gamut boundary
LPLUT cdecl _cmsComputeGamutLUT(cmsHPROFILE hProfile, int Intent);

// Compute softproof
LPLUT cdecl _cmsComputeSoftProofLUT(cmsHPROFILE hProfile, int nIntent);

// Find a suitable prelinearization tables, matching the given transform
void cdecl _cmsComputePrelinearizationTablesFromXFORM(cmsHTRANSFORM h[], int nTransforms, LPLUT Grid);


// Build a tone curve for K->K' if possible (only works on CMYK)
LPGAMMATABLE _cmsBuildKToneCurve(cmsHTRANSFORM hCMYK2CMYK, int nPoints);

// Validates a LUT
LCMSBOOL cdecl _cmsValidateLUT(LPLUT NewLUT);


// These are two VITAL macros, from converting between 8 and 16 bit
// representation.

#define RGB_8_TO_16(rgb) (WORD) ((((WORD) (rgb)) << 8)|(rgb))
#define RGB_16_TO_8(rgb) (BYTE) ((((rgb) * 65281 + 8388608) >> 24) & 0xFF)


#endif  // LCMS_APIONLY


#define __cms_H

#ifdef __cplusplus
}
#endif

#endif

