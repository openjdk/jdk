/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef HOTSPOT_JAVATYPES_HPP
#define HOTSPOT_JAVATYPES_HPP

#include "jni.h"
#include <cstdint>
#include "metaprogramming/primitiveConversions.hpp"

//----------------------------------------------------------------------------------------------------
// Constants

const int LogBytesPerShort   = 1;
const int LogBytesPerInt     = 2;
#ifdef _LP64
const int LogBytesPerWord    = 3;
#else
const int LogBytesPerWord    = 2;
#endif
const int LogBytesPerLong    = 3;

const int BytesPerShort      = 1 << LogBytesPerShort;
const int BytesPerInt        = 1 << LogBytesPerInt;
const int BytesPerWord       = 1 << LogBytesPerWord;
const int BytesPerLong       = 1 << LogBytesPerLong;

const int LogBitsPerByte     = 3;
const int LogBitsPerShort    = LogBitsPerByte + LogBytesPerShort;
const int LogBitsPerInt      = LogBitsPerByte + LogBytesPerInt;
const int LogBitsPerWord     = LogBitsPerByte + LogBytesPerWord;
const int LogBitsPerLong     = LogBitsPerByte + LogBytesPerLong;

const int BitsPerByte        = 1 << LogBitsPerByte;
const int BitsPerShort       = 1 << LogBitsPerShort;
const int BitsPerInt         = 1 << LogBitsPerInt;
const int BitsPerWord        = 1 << LogBitsPerWord;
const int BitsPerLong        = 1 << LogBitsPerLong;

const int WordAlignmentMask  = (1 << LogBytesPerWord) - 1;
const int LongAlignmentMask  = (1 << LogBytesPerLong) - 1;

const int oopSize            = sizeof(char*); // Full-width oop
extern int heapOopSize;                       // Oop within a java object
const int wordSize           = sizeof(char*);
const int longSize           = sizeof(jlong);
const int jintSize           = sizeof(jint);
const int size_tSize         = sizeof(size_t);

const int BytesPerOop        = BytesPerWord;  // Full-width oop

extern int LogBytesPerHeapOop;                // Oop within a java object
extern int LogBitsPerHeapOop;
extern int BytesPerHeapOop;
extern int BitsPerHeapOop;

const int BitsPerJavaInteger = 32;
const int BitsPerJavaLong    = 64;
const int BitsPerSize_t      = size_tSize * BitsPerByte;

// Additional Java basic types
typedef uint8_t  jubyte;
typedef uint16_t jushort;
typedef uint32_t juint;
typedef uint64_t julong;

// Unsigned byte types for os and stream.hpp

// Unsigned one, two, four and eight byte quantities used for describing
// the .class file format. See JVM book chapter 4.

typedef jubyte  u1;
typedef jushort u2;
typedef juint   u4;
typedef julong  u8;

const jubyte  max_jubyte  = (jubyte)-1;  // 0xFF       largest jubyte
const jushort max_jushort = (jushort)-1; // 0xFFFF     largest jushort
const juint   max_juint   = (juint)-1;   // 0xFFFFFFFF largest juint
const julong  max_julong  = (julong)-1;  // 0xFF....FF largest julong

typedef jbyte  s1;
typedef jshort s2;
typedef jint   s4;
typedef jlong  s8;

const jbyte min_jbyte = -(1 << 7);       // smallest jbyte
const jbyte max_jbyte = (1 << 7) - 1;    // largest jbyte
const jshort min_jshort = -(1 << 15);    // smallest jshort
const jshort max_jshort = (1 << 15) - 1; // largest jshort

//----------------------------------------------------------------------------------------------------
// Special casts
inline jint    jint_cast    (jfloat  x)  { return PrimitiveConversions::cast<jint>(x); }
inline jfloat  jfloat_cast  (jint    x)  { return PrimitiveConversions::cast<jfloat>(x); }

inline jlong   jlong_cast   (jdouble x)  { return PrimitiveConversions::cast<jlong>(x); }
inline julong  julong_cast  (jdouble x)  { return PrimitiveConversions::cast<julong>(x); }
inline jdouble jdouble_cast (jlong   x)  { return PrimitiveConversions::cast<jdouble>(x); }

inline jint low (jlong value)                    { return jint(value); }
inline jint high(jlong value)                    { return jint(value >> 32); }

// the fancy casts are a hopefully portable way
// to do unsigned 32 to 64 bit type conversion
inline void set_low (jlong* value, jint low )    { *value &= (jlong)0xffffffff << 32;
  *value |= (jlong)(julong)(juint)low; }

inline void set_high(jlong* value, jint high)    { *value &= (jlong)(julong)(juint)0xffffffff;
  *value |= (jlong)high       << 32; }

inline jlong jlong_from(jint h, jint l) {
  jlong result = 0; // initialization to avoid warning
  set_high(&result, h);
  set_low(&result,  l);
  return result;
}

const jint min_jint = (jint)1 << (sizeof(jint)*BitsPerByte-1); // 0x80000000 == smallest jint
const jint max_jint = (juint)min_jint - 1;                     // 0x7FFFFFFF == largest jint

const jint min_jintFloat = (jint)(0x00000001);
const jfloat min_jfloat = jfloat_cast(min_jintFloat);
const jint max_jintFloat = (jint)(0x7f7fffff);
const jfloat max_jfloat = jfloat_cast(max_jintFloat);

//-------------------------------------------
// Constant for jlong (standardized by C++11)

// Build a 64bit integer constant
#define CONST64(x)  (x ## LL)
#define UCONST64(x) (x ## ULL)

const jlong min_jlong = CONST64(0x8000000000000000);
const jlong max_jlong = CONST64(0x7fffffffffffffff);

//-------------------------------------------
// Constant for jdouble
const jlong min_jlongDouble = CONST64(0x0000000000000001);
const jdouble min_jdouble = jdouble_cast(min_jlongDouble);
const jlong max_jlongDouble = CONST64(0x7fefffffffffffff);
const jdouble max_jdouble = jdouble_cast(max_jlongDouble);

#endif //HOTSPOT_JAVATYPES_HPP
