/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_JAVAARITHMETIC_HPP
#define SHARE_UTILITIES_JAVAARITHMETIC_HPP

#include <cstdint>
#include <limits>
#include "jni.h"

// Basic types' bounds
constexpr jbyte  min_jbyte  = std::numeric_limits<jbyte>::min();
constexpr jbyte  max_jbyte  = std::numeric_limits<jbyte>::max();
constexpr jshort min_jshort = std::numeric_limits<jshort>::min();
constexpr jshort max_jshort = std::numeric_limits<jshort>::max();
constexpr jint   min_jint   = std::numeric_limits<jint>::min();
constexpr jint   max_jint   = std::numeric_limits<jint>::max();
constexpr jlong  min_jlong  = std::numeric_limits<jlong>::min();
constexpr jlong  max_jlong  = std::numeric_limits<jlong>::max();

constexpr jfloat  min_jfloat      = std::numeric_limits<jfloat>::min();
constexpr jint    min_jintFloat   = 0x00000001;
constexpr jfloat  max_jfloat      = std::numeric_limits<jfloat>::max();
constexpr jint    max_jintFloat   = 0x7f7fffff;
constexpr jdouble min_jdouble     = std::numeric_limits<jdouble>::min();
constexpr jlong   min_jlongDouble = 0x0000000000000001;
constexpr jdouble max_jdouble     = std::numeric_limits<jdouble>::max();
constexpr jlong   max_jlongDouble = 0x7fefffffffffffff;

// Additional Java basic types
using jubyte  = uint8_t;
using jushort = uint16_t;
using juint   = uint32_t;
using julong  = uint64_t;

constexpr jubyte  max_jubyte  = std::numeric_limits<jubyte>::max();
constexpr jushort max_jushort = std::numeric_limits<jushort>::max();
constexpr juint   max_juint   = std::numeric_limits<juint>::max();
constexpr julong  max_julong  = std::numeric_limits<julong>::max();

using uint = unsigned int;

//----------------------------------------------------------------------------------------------------
// Sum and product which can never overflow: they wrap, just like the
// Java operations.  Note that we don't intend these to be used for
// general-purpose arithmetic: their purpose is to emulate Java
// operations.

// The goal of this code to avoid undefined or implementation-defined
// behavior.
#define JAVA_INTEGER_OP(OP, NAME, TYPE, UNSIGNED_TYPE)  \
inline constexpr TYPE NAME (TYPE in1, TYPE in2) {       \
  UNSIGNED_TYPE ures = static_cast<UNSIGNED_TYPE>(in1); \
  ures OP ## = static_cast<UNSIGNED_TYPE>(in2);         \
  return ures;                                          \
}

JAVA_INTEGER_OP(+, java_add, jint, juint)
JAVA_INTEGER_OP(-, java_subtract, jint, juint)
JAVA_INTEGER_OP(*, java_multiply, jint, juint)
JAVA_INTEGER_OP(+, java_add, jlong, julong)
JAVA_INTEGER_OP(-, java_subtract, jlong, julong)
JAVA_INTEGER_OP(*, java_multiply, jlong, julong)

#undef JAVA_INTEGER_OP

// Provide integer shift operations with Java semantics.  No overflow
// issues - left shifts simply discard shifted out bits.  No undefined
// behavior for large or negative shift quantities; instead the actual
// shift distance is the argument modulo the lhs value's size in bits.
// No undefined or implementation defined behavior for shifting negative
// values; left shift discards bits, right shift sign extends.  We use
// the same safe conversion technique as above for java_add and friends.
#define JAVA_INTEGER_SHIFT_OP(OP, NAME, TYPE, XTYPE)         \
inline constexpr TYPE NAME (TYPE lhs, jint rhs) {            \
  constexpr juint rhs_mask = (sizeof(TYPE) * 8) - 1;         \
  static_assert(rhs_mask == 31 || rhs_mask == 63, "sanity"); \
  XTYPE xres = static_cast<XTYPE>(lhs);                      \
  xres OP ## = (rhs & rhs_mask);                             \
  return xres;                                               \
}

JAVA_INTEGER_SHIFT_OP(<<, java_shift_left, jint, juint)
JAVA_INTEGER_SHIFT_OP(<<, java_shift_left, jlong, julong)
// For signed shift right, assume C++ implementation >> sign extends.
JAVA_INTEGER_SHIFT_OP(>>, java_shift_right, jint, jint)
JAVA_INTEGER_SHIFT_OP(>>, java_shift_right, jlong, jlong)
// For >>> use C++ unsigned >>.
JAVA_INTEGER_SHIFT_OP(>>, java_shift_right_unsigned, jint, juint)
JAVA_INTEGER_SHIFT_OP(>>, java_shift_right_unsigned, jlong, julong)

#undef JAVA_INTEGER_SHIFT_OP

//----------------------------------------------------------------------------------------------------
// The goal of this code is to provide saturating operations for int/uint.
// Checks overflow conditions and saturates the result to min_jint/max_jint.
#define SATURATED_INTEGER_OP(OP, NAME, TYPE1, TYPE2) \
inline constexpr int NAME (TYPE1 in1, TYPE2 in2) {   \
  jlong res = static_cast<jlong>(in1);               \
  res OP ## = static_cast<jlong>(in2);               \
  if (res > max_jint) {                              \
    res = max_jint;                                  \
  } else if (res < min_jint) {                       \
    res = min_jint;                                  \
  }                                                  \
  return res;                                        \
}

SATURATED_INTEGER_OP(+, saturated_add, int, int)
SATURATED_INTEGER_OP(+, saturated_add, int, uint)
SATURATED_INTEGER_OP(+, saturated_add, uint, int)
SATURATED_INTEGER_OP(+, saturated_add, uint, uint)

#undef SATURATED_INTEGER_OP

//----------------------------------------------------------------------------------------------------
// Provide methods to calculate the magic constants in transforming divisions
// by constants into series of multiplications and shifts
// TODO: have magic_long_unsigned_divide up and down when we can do 128-bit
// addition
void magic_int_divide_constants(jint d, jlong& M, jint& s);
void magic_int_unsigned_divide_constants_down(juint d, jlong& M, jint& s);
void magic_int_unsigned_divide_constants_up(juint d, jlong& M, jint& s);
void magic_long_divide_constants(jlong d, jlong& M, jint& s);
void magic_long_unsigned_divide_constants(julong d, jlong& M, jint& s, bool& magic_const_ovf);

#endif // SHARE_UTILITIES_JAVAARITHMETIC_HPP
