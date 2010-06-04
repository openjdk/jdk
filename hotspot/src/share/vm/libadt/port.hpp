/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#ifndef _PORT_
#define _PORT_
// Typedefs for portable compiling

#if defined(__GNUC__)

#define INTERFACE       #pragma interface
#define IMPLEMENTATION  #pragma implementation
//INTERFACE
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

// Access to the C++ class virtual function pointer
// Put the class in the macro
typedef void *VPTR;
// G++ puts it at the end of the base class
#define ACCESS_VPTR(class) VPTR&vptr(){return*(VPTR*)((char*)this+sizeof(class)-sizeof(void*));}

#elif defined(__TURBOC__)

#include <mem.h>
#include <string.h>
extern "C" int stricmp(const char *, const char *);
inline void bcopy(const void *s, void *d, int l) { memmove(d,s,l); }
inline void bzero(void *p, int l) { memset(p,0,l); }
inline int bcmp(const void *s, const void *d, int l) { return memcmp(s,d,l); }
inline int min( int a, int b) { return a < b ? a : b; }
inline int max( int a, int b) { return a > b ? a : b; }
//strcasecmp moved to globalDefinitions_visCPP.hpp
//inline int strcasecmp(const char *s1, const char *s2) { return stricmp(s1,s2); }
inline long abs( long x ) { return x < 0 ? -x : x; }
// Access to the C++ class virtual function pointer
// Put the class in the macro
typedef void near *VPTR;
// BorlandC puts it up front
#define ACCESS_VPTR(class) VPTR&vptr(){return*(VPTR*)this;}

#elif defined(__hpux)

#define INTERFACE
#define IMPLEMENTATION
#define signed
#include <strings.h>
#include <stdlib.h>
inline long min( long a, long b) { return a < b ? a : b; }
inline long max( long a, long b) { return a > b ? a : b; }
inline int min( int a, int b) { return a < b ? a : b; }
inline int max( int a, int b) { return a > b ? a : b; }
inline long abs( long x ) { return x < 0 ? -x : x; }

#elif defined(__MOTO__)
// Motorola's mcc
#define INTERFACE
#define IMPLEMENTATION
#include <stdlib.h>
#include <memory.h>
inline int min( int a, int b) { return a < b ? a : b; }
inline int max( int a, int b) { return a > b ? a : b; }

#elif defined(_AIX)
// IBM's xlC compiler
#define INTERFACE
#define IMPLEMENTATION
#include <stdlib.h>
#include <memory.h>
inline int min( int a, int b) { return a < b ? a : b; }
inline int max( int a, int b) { return a > b ? a : b; }

#elif defined(_MSC_VER)
// Microsoft Visual C++
//#define INTERFACE
#define IMPLEMENTATION
#include <stdlib.h>
#undef small
//strcasecmp moved to globalDefinitions_visCPP.hpp
//inline int strcasecmp(const char *s1, const char *s2) { return stricmp(s1,s2); }


#elif defined(SPARC_WORKS)

#define INTERFACE
#define IMPLEMENTATION

#include <stddef.h>
#include <stdlib.h>
#include <string.h>

#elif defined(SOLARIS)

#define INTERFACE
#define IMPLEMENTATION

#include <stddef.h>
#include <stdlib.h>
#include <string.h>


#elif defined(__TANDEM)

// This case is for the Tandem Business Unit of Compaq Computer Corporation.
// The Tandem case must precede the AT&T case,
// because the Tandem c89 compiler also defines __cplusplus.

#include "port_tandem.hpp"

#elif defined(__cplusplus)
// AT&Ts cfront
#define INTERFACE
#define IMPLEMENTATION
#include <unistd.h>
#define signed
// #include <bstring.h>
inline int min( int a, int b) { return a < b ? a : b; }
inline int max( int a, int b) { return a > b ? a : b; }

#else  // All other machines

#define signed
extern "C" void bcopy(void *b1, void *b2, int len);
inline int min( int a, int b) { return a < b ? a : b; }
inline int max( int a, int b) { return a > b ? a : b; }

#endif

//-----------------------------------------------------------------------------
// Safer memory allocations
#ifdef SAFE_MEMORY
#define malloc(size)        safe_malloc(__FILE__,__LINE__,size)
#define free(ptr)           safe_free(__FILE__,__LINE__,ptr)
#define realloc(ptr,size)   safe_realloc(__FILE__,__LINE__,ptr,size)
#define calloc(nitems,size) safe_calloc(__FILE__,__LINE__,nitems,size)
#define strdup(ptr)         safe_strdup(__FILE__,__LINE__,ptr)
extern void *safe_malloc (const char *file, unsigned line, unsigned size);
extern void  safe_free   (const char *file, unsigned line, void *ptr);
extern void *safe_calloc (const char *file, unsigned line, unsigned nitems, unsigned size);
extern void *safe_realloc(const char *file, unsigned line, void *ptr, unsigned size);
extern char *safe_strdup (const char *file, unsigned line, const char *src);
inline void *operator new( size_t size ) { return malloc(size); }
inline void operator delete( void *ptr ) { free(ptr); }
#endif

//-----------------------------------------------------------------------------
// And now, the bit-size-specified integer sizes
typedef signed char int8;
typedef unsigned char uint8;
typedef unsigned char byte;

// All uses of *int16 changed to 32-bit to speed up compiler on Intel
//typedef signed short int16;   // Exactly 16bits signed
//typedef unsigned short uint16;        // Exactly 16bits unsigned
//const unsigned int min_uint16 = 0x0000;    // smallest uint16
//const unsigned int max_uint16 = 0xFFFF;    // largest  uint16

typedef unsigned int uint;      // When you need a fast >=16bit unsigned value
/*typedef int int; */           // When you need a fast >=16bit value
const unsigned int max_uint = (uint)-1;
typedef int32_t int32;   // Exactly 32bits signed
typedef uint32_t uint32; // Exactly 32bits unsigned

// Bit-sized floating point and long thingies
#ifndef __TANDEM
// Do not define these for Tandem, because they conflict with typedefs in softieee.h.
typedef float float32;          // 32-bit float
typedef double float64;         // 64-bit float
#endif // __TANDEM

typedef jlong int64;            // Java long for my 64-bit type
typedef julong uint64;          // Java long for my 64-bit type

//-----------------------------------------------------------------------------
// Nice constants
uint32 gcd( uint32 x, uint32 y );
int ff1( uint32 mask );
int fh1( uint32 mask );
uint32 rotate32( uint32 x, int32 cnt );


//-----------------------------------------------------------------------------
extern uint32 heap_totalmem;      // Current total memory allocation
extern uint32 heap_highwater;     // Highwater mark to date for memory usage

#endif // _PORT_
