/*
 * Portions Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * (C) Copyright Taligent, Inc.,  1996, 1997                                 *
 * (C) Copyright IBM Corp. 1998-2003 - All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by IBM. These materials are provided
 * under terms of a License Agreement between IBM and Sun.
 * This technology is protected by multiple US and International
 * patents. This notice and attribution to IBM may not be removed.
 */

/*
*
*  FILE NAME : UTYPES.H (formerly ptypes.h)
*
*   Date        Name        Description
*   12/11/96    helena      Creation.
*   02/27/97    aliu        Added typedefs for UClassID, int8, int16, int32,
*                           uint8, uint16, and uint32.
*   04/01/97    aliu        Added XP_CPLUSPLUS and modified to work under C as
*                            well as C++.
*                           Modified to use memcpy() for icu_arrayCopy() fns.
*   04/14/97    aliu        Added TPlatformUtilities.
*   05/07/97    aliu        Added import/export specifiers (replacing the old
*                           broken EXT_CLASS).  Added version number for our
*                           code.  Cleaned up header.
*    6/20/97    helena      Java class name change.
*   08/11/98    stephen     UErrorCode changed from typedef to enum
*   08/12/98    erm         Changed T_ANALYTIC_PACKAGE_VERSION to 3
*   08/14/98    stephen     Added icu_arrayCopy() for int8_t, int16_t, int32_t
*   12/09/98    jfitz       Added BUFFER_OVERFLOW_ERROR (bug 1100066)
*   04/20/99    stephen     Cleaned up & reworked for autoconf.
*                           Renamed to utypes.h.
*   05/05/99    stephen     Changed to use <inttypes.h>
*   10/12/00    dfelt       Adapted to use javavm types (plus juint from j2d)
*******************************************************************************
*/

#ifndef UTYPES_H
#define UTYPES_H

// doesn't seem to be a way of getting around this

//#if defined(WIN32) || defined(_WIN32)
// not needed since we don't export to another dll

#if 0

#define U_EXPORT __declspec(dllexport)
#define U_EXPORT2
#define U_IMPORT __declspec(dllimport)

#else

#define U_EXPORT
#define U_EXPORT2
#define U_IMPORT

#endif

#ifdef XP_CPLUSPLUS
#   define U_CFUNC extern "C"
#   define U_CDECL_BEGIN extern "C" {
#   define U_CDECL_END   }
#else
#   define U_CFUNC
#   define U_CDECL_BEGIN
#   define U_CDECL_END
#endif
#define U_CAPI U_CFUNC U_EXPORT

// defines jboolean, jchar, jshort, and jint via jni_md.h
#include "jni.h"
// defines jubyte, jushort, juint
#include "j2d_md.h"

typedef jboolean bool_t;

/*
 * win32 does not have standard definitions for the following:
 */
#ifdef WIN32

#ifndef __int8_t_defined
typedef jbyte    int8_t;
typedef jshort   int16_t;
typedef jint     int32_t;
#endif

typedef jubyte   uint8_t;
typedef jushort  uint16_t;
typedef juint    uint32_t;

#endif /* WIN32 */

/*===========================================================================*/
/* Unicode character                                                         */
/*===========================================================================*/
typedef uint16_t UChar;


/** Error code to replace exception handling */
enum UErrorCode {
    U_ERROR_INFO_START        = -128,     /* Start of information results (semantically successful) */
    U_USING_FALLBACK_ERROR    = -128,
    U_USING_DEFAULT_ERROR     = -127,
    U_ERROR_INFO_LIMIT,

    U_ZERO_ERROR              =  0,       /* success */

    U_ILLEGAL_ARGUMENT_ERROR  =  1,       /* Start of codes indicating failure */
    U_MISSING_RESOURCE_ERROR  =  2,
    U_INVALID_FORMAT_ERROR    =  3,
    U_FILE_ACCESS_ERROR       =  4,
    U_INTERNAL_PROGRAM_ERROR  =  5,       /* Indicates a bug in the library code */
    U_MESSAGE_PARSE_ERROR     =  6,
    U_MEMORY_ALLOCATION_ERROR =  7,       /* Memory allocation error */
    U_INDEX_OUTOFBOUNDS_ERROR =  8,
    U_PARSE_ERROR             =  9,       /* Equivalent to Java ParseException */
    U_INVALID_CHAR_FOUND      = 10,       /* In the Character conversion routines: Invalid character or sequence was encountered*/
    U_TRUNCATED_CHAR_FOUND    = 11,       /* In the Character conversion routines: More bytes are required to complete the conversion successfully*/
    U_ILLEGAL_CHAR_FOUND      = 12,       /* In codeset conversion: a sequence that does NOT belong in the codepage has been encountered*/
    U_INVALID_TABLE_FORMAT    = 13,       /* Conversion table file found, but corrupted*/
    U_INVALID_TABLE_FILE      = 14,       /* Conversion table file not found*/
    U_BUFFER_OVERFLOW_ERROR   = 15,       /* A result would not fit in the supplied buffer */
    U_UNSUPPORTED_ERROR       = 16,       /* Requested operation not supported in current context */
    U_ERROR_LIMIT
};

#ifndef XP_CPLUSPLUS
typedef enum UErrorCode UErrorCode;
#endif

/* Use the following to determine if an UErrorCode represents */
/* operational success or failure. */
#ifdef XP_CPLUSPLUS
inline bool_t U_SUCCESS(UErrorCode code) { return (bool_t)(code<=U_ZERO_ERROR); }
inline bool_t U_FAILURE(UErrorCode code) { return (bool_t)(code>U_ZERO_ERROR); }
#else
#define U_SUCCESS(x) ((x)<=U_ZERO_ERROR)
#define U_FAILURE(x) ((x)>U_ZERO_ERROR)
#endif

#ifndef TRUE
#   define TRUE  1
#endif
#ifndef FALSE
#   define FALSE 0
#endif

// UTYPES_H
#endif
