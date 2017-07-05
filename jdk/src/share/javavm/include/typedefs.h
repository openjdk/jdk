/*
 * Copyright 1994-2002 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef _JAVASOFT_TYPEDEFS_H_
#define _JAVASOFT_TYPEDEFS_H_

#include "typedefs_md.h"        /* for int64_t */

/*
 * Macros to deal with the JavaVM's stack alignment. Many machines
 * require doublewords to be double aligned.  This union is used by
 * code in math.h as a more portable way do alingnment on machines
 * that require it.  This union and the macros that use it came from
 * Netscape.
 */

#ifdef HAVE_ALIGNED_LONGLONGS
#define GET_INT64(_t,_addr)                                \
        ((((int32_t*) &(_t))[0] = ((int32_t*)(_addr))[0]), \
         (((int32_t*) &(_t))[1] = ((int32_t*)(_addr))[1]), \
         (_t).j )
#define SET_INT64(_t, _addr, _v)                           \
        ( (_t).j = (_v),                                   \
          ((int32_t*)(_addr))[0] = ((int32_t*) &(_t))[0],  \
          ((int32_t*)(_addr))[1] = ((int32_t*) &(_t))[1] )
#else
#define GET_INT64(_t,_addr) (*(int64_t*)(_addr))
#define SET_INT64(_t, _addr, _v) (*(int64_t*)(_addr) = (_v))
#endif

/* If double's must be aligned on doubleword boundaries then define this */
#ifdef HAVE_ALIGNED_DOUBLES
#define GET_DOUBLE(_t,_addr)                               \
        ((((int32_t*) &(_t))[0] = ((int32_t*)(_addr))[0]), \
         (((int32_t*) &(_t))[1] = ((int32_t*)(_addr))[1]), \
         (_t).d )
#define SET_DOUBLE(_t, _addr, _v)                          \
        ( (_t).d = (_v),                                   \
          ((int32_t*)(_addr))[0] = ((int32_t*) &(_t))[0],  \
          ((int32_t*)(_addr))[1] = ((int32_t*) &(_t))[1] )
#else
#define GET_DOUBLE(_t,_addr) (*(jdouble*)(_addr))
#define SET_DOUBLE(_t, _addr, _v) (*(jdouble*)(_addr) = (_v))
#endif

/* If pointers are 64bits then define this */
#ifdef HAVE_64BIT_POINTERS
#define GET_HANDLE(_t,_addr)                               \
        ( ((int32_t*) &(_t))[0] = ((int32_t*)(_addr))[0]), \
          ((int32_t*) &(_t))[1] = ((int32_t*)(_addr))[1]), \
          (void*) (_t).l )
#define SET_HANDLE(_t, _addr, _v)                          \
        ( *(void**) &((_t).l) = (_v),                      \
          ((int32_t*)(_addr))[0] = ((int32_t*) &(_t))[0],  \
          ((int32_t*)(_addr))[1] = ((int32_t*) &(_t))[1] )
#else
#define GET_HANDLE(_t,_addr) (*(JHandle*)(_addr))
#define SET_HANDLE(_t, _addr, _v) (*(JHandle*)(_addr) = (_v))
#endif


/*
 *   Printf-style formatters for fixed- and variable-width types as pointers and
 *   integers.
 *
 * Each platform-specific definitions file "typedefs_md.h"
 * must define the macro FORMAT64_MODIFIER, which is the modifier for '%x' or
 * '%d' formats to indicate a 64-bit quantity; commonly "l" (in LP64) or "ll"
 * (in ILP32).
 */

/* Format 32-bit quantities. */
#define INT32_FORMAT  "%d"
#define UINT32_FORMAT "%u"
#define PTR32_FORMAT  "0x%08x"

/* Format 64-bit quantities. */
#define INT64_FORMAT  "%" FORMAT64_MODIFIER "d"
#define UINT64_FORMAT "%" FORMAT64_MODIFIER "u"
#define PTR64_FORMAT  "0x%016" FORMAT64_MODIFIER "x"

/* Format pointers and size_t (or size_t-like integer types) which change size
 *  between 32- and 64-bit.
 */
#if defined(_LP64) || defined(_WIN64)
#define PTR_FORMAT    PTR64_FORMAT
#define SIZE_FORMAT   UINT64_FORMAT
#define SSIZE_FORMAT  INT64_FORMAT
#else
#define PTR_FORMAT    PTR32_FORMAT
#define SIZE_FORMAT   UINT32_FORMAT
#define SSIZE_FORMAT  INT32_FORMAT
#endif

#define INTPTR_FORMAT PTR_FORMAT


#endif /* !_JAVASOFT_TYPEDEFS_H_ */
