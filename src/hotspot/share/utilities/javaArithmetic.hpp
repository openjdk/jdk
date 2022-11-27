#ifndef SHARE_UTILITIES_JAVAARITHMETIC_HPP
#define SHARE_UTILITIES_JAVAARITHMETIC_HPP

#include "jni.h"
#include <type_traits>

// Additional Java basic types
using jubyte  = std::make_unsigned<jbyte> ::type;
using jushort = std::make_unsigned<jshort>::type;
using juint   = std::make_unsigned<jint>  ::type;
using julong  = std::make_unsigned<jlong> ::type;

//----------------------------------------------------------------------------------------------------
// Sum and product which can never overflow: they wrap, just like the
// Java operations.  Note that we don't intend these to be used for
// general-purpose arithmetic: their purpose is to emulate Java
// operations.

// The goal of this code to avoid undefined or implementation-defined
// behavior.
#define JAVA_INTEGER_OP(OP, NAME, TYPE, UNSIGNED_TYPE)  \
inline TYPE NAME (TYPE in1, TYPE in2) {                 \
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
#define JAVA_INTEGER_SHIFT_OP(OP, NAME, TYPE, XTYPE)    \
inline TYPE NAME (TYPE lhs, jint rhs) {                 \
  constexpr juint rhs_mask = (sizeof(TYPE) * 8) - 1;         \
  static_assert(rhs_mask == 31 || rhs_mask == 63);      \
  XTYPE xres = static_cast<XTYPE>(lhs);                 \
  xres OP ## = (rhs & rhs_mask);                        \
  return xres;                                          \
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
inline int NAME (TYPE1 in1, TYPE2 in2) {             \
  jlong res = static_cast<jlong>(in1);               \
  res OP ## = static_cast<jlong>(in2);               \
  constexpr jint max_jint = (jint)0x7FFFFFFF;        \
  constexpr jint min_jint = (jint)0x80000000;        \
  if (res > max_jint) {                              \
    res = max_jint;                                  \
  } else if (res < min_jint) {                       \
    res = min_jint;                                  \
  }                                                  \
  return res;                                        \
}

SATURATED_INTEGER_OP(+, saturated_add, jint, jint)
SATURATED_INTEGER_OP(+, saturated_add, jint, juint)
SATURATED_INTEGER_OP(+, saturated_add, juint, jint)
SATURATED_INTEGER_OP(+, saturated_add, juint, juint)

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
