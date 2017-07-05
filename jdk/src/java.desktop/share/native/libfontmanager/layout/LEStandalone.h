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
 *
 */

#ifndef __LESTANDALONE
#define __LESTANDALONE

#ifndef U_COPYRIGHT_STRING
#define U_COPYRIGHT_STRING " (C) Copyright IBM Corp and Others. 1998-2010 - All Rights Reserved"
#endif

/* ICU Version number */
#ifndef U_ICU_VERSION
#define U_ICU_VERSION "4.6"
#endif

/* Definitions to make Layout Engine work away from ICU. */
#ifndef U_NAMESPACE_BEGIN
#define U_NAMESPACE_BEGIN
#endif

#ifndef U_NAMESPACE_END
#define U_NAMESPACE_END
#endif

/* RTTI Definition */
typedef const char *UClassID;
#ifndef UOBJECT_DEFINE_RTTI_IMPLEMENTATION
#define UOBJECT_DEFINE_RTTI_IMPLEMENTATION(x) UClassID x::getStaticClassID(){static char z=0; return (UClassID)&z; } UClassID x::getDynamicClassID() const{return x::getStaticClassID(); }
#endif

/* UMemory's functions aren't used by the layout engine. */
struct UMemory {};
/* UObject's functions aren't used by the layout engine. */
struct UObject {};

/* String handling */
#include <stdlib.h>
#include <string.h>

/**
 * A convenience macro to test for the success of a LayoutEngine call.
 *
 * @stable ICU 2.4
 */
#define LE_SUCCESS(code) ((code)<=LE_NO_ERROR)

/**
 * A convenience macro to test for the failure of a LayoutEngine call.
 *
 * @stable ICU 2.4
 */
#define LE_FAILURE(code) ((code)>LE_NO_ERROR)


#ifndef _LP64
typedef long le_int32;
typedef unsigned long le_uint32;
#else
typedef int le_int32;
typedef unsigned int le_uint32;
#endif

#define HAVE_LE_INT32 1
#define HAVE_LE_UINT32 1

typedef unsigned short UChar;
typedef le_uint32 UChar32;

typedef short le_int16;
#define HAVE_LE_INT16 1

typedef unsigned short le_uint16;
#define HAVE_LE_UINT16

typedef signed char le_int8;
#define HAVE_LE_INT8

typedef unsigned char le_uint8;
#define HAVE_LE_UINT8

typedef char UBool;

/**
 * Error codes returned by the LayoutEngine.
 *
 * @stable ICU 2.4
 */
enum LEErrorCode {
    /* informational */
    LE_NO_SUBFONT_WARNING           = -127, // U_USING_DEFAULT_WARNING,

    /* success */
    LE_NO_ERROR                     = 0, // U_ZERO_ERROR,

    /* failures */
    LE_ILLEGAL_ARGUMENT_ERROR       = 1, // U_ILLEGAL_ARGUMENT_ERROR,
    LE_MEMORY_ALLOCATION_ERROR      = 7, // U_MEMORY_ALLOCATION_ERROR,
    LE_INDEX_OUT_OF_BOUNDS_ERROR    = 8, //U_INDEX_OUTOFBOUNDS_ERROR,
    LE_NO_LAYOUT_ERROR              = 16, // U_UNSUPPORTED_ERROR,
    LE_INTERNAL_ERROR               = 5, // U_INTERNAL_PROGRAM_ERROR,
    LE_FONT_FILE_NOT_FOUND_ERROR    = 4, // U_FILE_ACCESS_ERROR,
    LE_MISSING_FONT_TABLE_ERROR     = 2  // U_MISSING_RESOURCE_ERROR
};
#define HAVE_LEERRORCODE

#define U_LAYOUT_API

#define uprv_malloc malloc
#define uprv_free free
#define uprv_memcpy memcpy
#define uprv_realloc realloc

#define U_EXPORT2
#define U_CAPI extern "C"

#if !defined(U_IS_BIG_ENDIAN)
    #ifdef _LITTLE_ENDIAN
        #define U_IS_BIG_ENDIAN 0
    #endif
#endif

#endif
