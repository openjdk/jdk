/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/signature.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

// Basic error support

// Info for oops within a java object.  Defaults are zero so
// things will break badly if incorrectly initialized.
int heapOopSize        = 0;
int LogBytesPerHeapOop = 0;
int LogBitsPerHeapOop  = 0;
int BytesPerHeapOop    = 0;
int BitsPerHeapOop     = 0;

// Old CDS options
bool RequireSharedSpaces;
extern "C" {
JNIEXPORT jboolean UseSharedSpaces = true;
}

// Object alignment, in units of HeapWords.
// Defaults are -1 so things will break badly if incorrectly initialized.
int MinObjAlignment            = -1;
int MinObjAlignmentInBytes     = -1;
int MinObjAlignmentInBytesMask = 0;

int LogMinObjAlignment         = -1;
int LogMinObjAlignmentInBytes  = -1;

// Oop encoding heap max
uint64_t OopEncodingHeapMax = 0;

const int LockingMode = LM_LIGHTWEIGHT;

// Something to help porters sleep at night

#ifdef ASSERT
static BasicType char2type(int ch) {
  switch (ch) {
#define EACH_SIG(ch, bt, ignore) \
    case ch: return bt;
    SIGNATURE_TYPES_DO(EACH_SIG, ignore)
#undef EACH_SIG
  }
  return T_ILLEGAL;
}

extern bool signature_constants_sane();
#endif //ASSERT

void basic_types_init() {
#ifdef ASSERT
#ifdef _LP64
  static_assert(min_intx ==  (intx)CONST64(0x8000000000000000), "correct constant");
  static_assert(max_intx ==  CONST64(0x7FFFFFFFFFFFFFFF), "correct constant");
  static_assert(max_uintx == CONST64(0xFFFFFFFFFFFFFFFF), "correct constant");
  static_assert( 8 == sizeof( intx),      "wrong size for basic type");
  static_assert( 8 == sizeof( jobject),   "wrong size for basic type");
#else
  static_assert(min_intx ==  (intx)0x80000000,  "correct constant");
  static_assert(max_intx ==  0x7FFFFFFF,  "correct constant");
  static_assert(max_uintx == 0xFFFFFFFF,  "correct constant");
  static_assert( 4 == sizeof( intx),      "wrong size for basic type");
  static_assert( 4 == sizeof( jobject),   "wrong size for basic type");
#endif
  static_assert( (~max_juint) == 0,  "max_juint has all its bits");
  static_assert( (~max_uintx) == 0,  "max_uintx has all its bits");
  static_assert( (~max_julong) == 0, "max_julong has all its bits");
  static_assert( 1 == sizeof( jbyte),     "wrong size for basic type");
  static_assert( 2 == sizeof( jchar),     "wrong size for basic type");
  static_assert( 2 == sizeof( jshort),    "wrong size for basic type");
  static_assert( 4 == sizeof( juint),     "wrong size for basic type");
  static_assert( 4 == sizeof( jint),      "wrong size for basic type");
  static_assert( 1 == sizeof( jboolean),  "wrong size for basic type");
  static_assert( 8 == sizeof( jlong),     "wrong size for basic type");
  static_assert( 4 == sizeof( jfloat),    "wrong size for basic type");
  static_assert( 8 == sizeof( jdouble),   "wrong size for basic type");
  static_assert( 1 == sizeof( u1),        "wrong size for basic type");
  static_assert( 2 == sizeof( u2),        "wrong size for basic type");
  static_assert( 4 == sizeof( u4),        "wrong size for basic type");
  static_assert(wordSize == BytesPerWord, "should be the same since they're used interchangeably");
  static_assert(wordSize == HeapWordSize, "should be the same since they're also used interchangeably");

  assert(signature_constants_sane(), "");

  int num_type_chars = 0;
  for (int i = 0; i < 99; i++) {
    if (type2char((BasicType)i) != 0) {
      assert(char2type(type2char((BasicType)i)) == i, "proper inverses");
      assert(Signature::basic_type(type2char((BasicType)i)) == i, "proper inverses");
      num_type_chars++;
    }
  }
  assert(num_type_chars == 11, "must have tested the right number of mappings");
  assert(char2type(0) == T_ILLEGAL, "correct illegality");

  {
    for (int i = T_BOOLEAN; i <= T_CONFLICT; i++) {
      BasicType vt = (BasicType)i;
      BasicType ft = type2field[vt];
      switch (vt) {
      // the following types might plausibly show up in memory layouts:
      case T_BOOLEAN:
      case T_BYTE:
      case T_CHAR:
      case T_SHORT:
      case T_INT:
      case T_FLOAT:
      case T_DOUBLE:
      case T_LONG:
      case T_OBJECT:
      case T_ADDRESS:     // random raw pointer
      case T_METADATA:    // metadata pointer
      case T_NARROWOOP:   // compressed pointer
      case T_NARROWKLASS: // compressed klass pointer
      case T_CONFLICT:    // might as well support a bottom type
      case T_VOID:        // padding or other unaddressed word
        // layout type must map to itself
        assert(vt == ft, "");
        break;
      default:
        // non-layout type must map to a (different) layout type
        assert(vt != ft, "");
        assert(ft == type2field[ft], "");
      }
      // every type must map to same-sized layout type:
      assert(type2size[vt] == type2size[ft], "");
    }
  }
  // These are assumed, e.g., when filling HeapWords with juints.
  static_assert(is_power_of_2(sizeof(juint)), "juint must be power of 2");
  static_assert(is_power_of_2(HeapWordSize), "HeapWordSize must be power of 2");
  static_assert((size_t)HeapWordSize >= sizeof(juint),
                "HeapWord should be at least as large as juint");
#endif

  if( JavaPriority1_To_OSPriority != -1 )
    os::java_to_os_priority[1] = JavaPriority1_To_OSPriority;
  if( JavaPriority2_To_OSPriority != -1 )
    os::java_to_os_priority[2] = JavaPriority2_To_OSPriority;
  if( JavaPriority3_To_OSPriority != -1 )
    os::java_to_os_priority[3] = JavaPriority3_To_OSPriority;
  if( JavaPriority4_To_OSPriority != -1 )
    os::java_to_os_priority[4] = JavaPriority4_To_OSPriority;
  if( JavaPriority5_To_OSPriority != -1 )
    os::java_to_os_priority[5] = JavaPriority5_To_OSPriority;
  if( JavaPriority6_To_OSPriority != -1 )
    os::java_to_os_priority[6] = JavaPriority6_To_OSPriority;
  if( JavaPriority7_To_OSPriority != -1 )
    os::java_to_os_priority[7] = JavaPriority7_To_OSPriority;
  if( JavaPriority8_To_OSPriority != -1 )
    os::java_to_os_priority[8] = JavaPriority8_To_OSPriority;
  if( JavaPriority9_To_OSPriority != -1 )
    os::java_to_os_priority[9] = JavaPriority9_To_OSPriority;
  if(JavaPriority10_To_OSPriority != -1 )
    os::java_to_os_priority[10] = JavaPriority10_To_OSPriority;

  // Set the size of basic types here (after argument parsing but before
  // stub generation).
  if (UseCompressedOops) {
    // Size info for oops within java objects is fixed
    heapOopSize        = jintSize;
    LogBytesPerHeapOop = LogBytesPerInt;
    LogBitsPerHeapOop  = LogBitsPerInt;
    BytesPerHeapOop    = BytesPerInt;
    BitsPerHeapOop     = BitsPerInt;
  } else {
    heapOopSize        = oopSize;
    LogBytesPerHeapOop = LogBytesPerWord;
    LogBitsPerHeapOop  = LogBitsPerWord;
    BytesPerHeapOop    = BytesPerWord;
    BitsPerHeapOop     = BitsPerWord;
  }
  _type2aelembytes[T_OBJECT] = heapOopSize;
  _type2aelembytes[T_ARRAY]  = heapOopSize;
}


// Map BasicType to signature character
char type2char_tab[T_CONFLICT+1] = {
  0, 0, 0, 0,
  JVM_SIGNATURE_BOOLEAN, JVM_SIGNATURE_CHAR,
  JVM_SIGNATURE_FLOAT,   JVM_SIGNATURE_DOUBLE,
  JVM_SIGNATURE_BYTE,    JVM_SIGNATURE_SHORT,
  JVM_SIGNATURE_INT,     JVM_SIGNATURE_LONG,
  JVM_SIGNATURE_CLASS,   JVM_SIGNATURE_ARRAY,
  JVM_SIGNATURE_VOID,    0,
  0, 0, 0, 0
};

// Map BasicType to Java type name
const char* type2name_tab[T_CONFLICT+1] = {
  nullptr, nullptr, nullptr, nullptr,
  "boolean",
  "char",
  "float",
  "double",
  "byte",
  "short",
  "int",
  "long",
  "object",
  "array",
  "void",
  "*address*",
  "*narrowoop*",
  "*metadata*",
  "*narrowklass*",
  "*conflict*"
};
const char* type2name(BasicType t) {
  if (t < ARRAY_SIZE(type2name_tab)) {
    return type2name_tab[t];
  } else if (t == T_ILLEGAL) {
    return "*illegal*";
  } else {
    fatal("invalid type %d", t);
    return "invalid type";
  }
}



BasicType name2type(const char* name) {
  for (int i = T_BOOLEAN; i <= T_VOID; i++) {
    BasicType t = (BasicType)i;
    if (type2name_tab[t] != nullptr && 0 == strcmp(type2name_tab[t], name))
      return t;
  }
  return T_ILLEGAL;
}

// Map BasicType to size in words
int type2size[T_CONFLICT+1]={ -1, 0, 0, 0, 1, 1, 1, 2, 1, 1, 1, 2, 1, 1, 0, 1, 1, 1, 1, -1};

BasicType type2field[T_CONFLICT+1] = {
  (BasicType)0,            // 0,
  (BasicType)0,            // 1,
  (BasicType)0,            // 2,
  (BasicType)0,            // 3,
  T_BOOLEAN,               // T_BOOLEAN  =  4,
  T_CHAR,                  // T_CHAR     =  5,
  T_FLOAT,                 // T_FLOAT    =  6,
  T_DOUBLE,                // T_DOUBLE   =  7,
  T_BYTE,                  // T_BYTE     =  8,
  T_SHORT,                 // T_SHORT    =  9,
  T_INT,                   // T_INT      = 10,
  T_LONG,                  // T_LONG     = 11,
  T_OBJECT,                // T_OBJECT   = 12,
  T_OBJECT,                // T_ARRAY    = 13,
  T_VOID,                  // T_VOID     = 14,
  T_ADDRESS,               // T_ADDRESS  = 15,
  T_NARROWOOP,             // T_NARROWOOP= 16,
  T_METADATA,              // T_METADATA = 17,
  T_NARROWKLASS,           // T_NARROWKLASS = 18,
  T_CONFLICT               // T_CONFLICT = 19,
};


BasicType type2wfield[T_CONFLICT+1] = {
  (BasicType)0,            // 0,
  (BasicType)0,            // 1,
  (BasicType)0,            // 2,
  (BasicType)0,            // 3,
  T_INT,     // T_BOOLEAN  =  4,
  T_INT,     // T_CHAR     =  5,
  T_FLOAT,   // T_FLOAT    =  6,
  T_DOUBLE,  // T_DOUBLE   =  7,
  T_INT,     // T_BYTE     =  8,
  T_INT,     // T_SHORT    =  9,
  T_INT,     // T_INT      = 10,
  T_LONG,    // T_LONG     = 11,
  T_OBJECT,  // T_OBJECT   = 12,
  T_OBJECT,  // T_ARRAY    = 13,
  T_VOID,    // T_VOID     = 14,
  T_ADDRESS, // T_ADDRESS  = 15,
  T_NARROWOOP, // T_NARROWOOP  = 16,
  T_METADATA,  // T_METADATA   = 17,
  T_NARROWKLASS, // T_NARROWKLASS  = 18,
  T_CONFLICT // T_CONFLICT = 19,
};


int _type2aelembytes[T_CONFLICT+1] = {
  0,                         // 0
  0,                         // 1
  0,                         // 2
  0,                         // 3
  T_BOOLEAN_aelem_bytes,     // T_BOOLEAN  =  4,
  T_CHAR_aelem_bytes,        // T_CHAR     =  5,
  T_FLOAT_aelem_bytes,       // T_FLOAT    =  6,
  T_DOUBLE_aelem_bytes,      // T_DOUBLE   =  7,
  T_BYTE_aelem_bytes,        // T_BYTE     =  8,
  T_SHORT_aelem_bytes,       // T_SHORT    =  9,
  T_INT_aelem_bytes,         // T_INT      = 10,
  T_LONG_aelem_bytes,        // T_LONG     = 11,
  T_OBJECT_aelem_bytes,      // T_OBJECT   = 12,
  T_ARRAY_aelem_bytes,       // T_ARRAY    = 13,
  0,                         // T_VOID     = 14,
  T_OBJECT_aelem_bytes,      // T_ADDRESS  = 15,
  T_NARROWOOP_aelem_bytes,   // T_NARROWOOP= 16,
  T_OBJECT_aelem_bytes,      // T_METADATA = 17,
  T_NARROWKLASS_aelem_bytes, // T_NARROWKLASS= 18,
  0                          // T_CONFLICT = 19,
};

#ifdef ASSERT
int type2aelembytes(BasicType t, bool allow_address) {
  assert((allow_address || t != T_ADDRESS) && t <= T_CONFLICT, "unexpected basic type");
  return _type2aelembytes[t];
}
#endif

// Support for 64-bit integer arithmetic

// The following code is mostly taken from JVM typedefs_md.h and system_md.c

static const jlong high_bit   = (jlong)1 << (jlong)63;
static const jlong other_bits = ~high_bit;

jlong float2long(jfloat f) {
  jlong tmp = (jlong) f;
  if (tmp != high_bit) {
    return tmp;
  } else {
    if (g_isnan((jdouble)f)) {
      return 0;
    }
    if (f < 0) {
      return high_bit;
    } else {
      return other_bits;
    }
  }
}


jlong double2long(jdouble f) {
  jlong tmp = (jlong) f;
  if (tmp != high_bit) {
    return tmp;
  } else {
    if (g_isnan(f)) {
      return 0;
    }
    if (f < 0) {
      return high_bit;
    } else {
      return other_bits;
    }
  }
}

// least common multiple
size_t lcm(size_t a, size_t b) {
    size_t cur, div, next;

    cur = MAX2(a, b);
    div = MIN2(a, b);

    assert(div != 0, "lcm requires positive arguments");


    while ((next = cur % div) != 0) {
        cur = div; div = next;
    }


    julong result = julong(a) * b / div;
    assert(result <= (size_t)max_uintx, "Integer overflow in lcm");

    return size_t(result);
}


// Test that nth_bit macro and friends behave as
// expected, even with low-precedence operators.

STATIC_ASSERT(nth_bit(3)   == 0x8);
STATIC_ASSERT(nth_bit(1|2) == 0x8);

STATIC_ASSERT(right_n_bits(3)   == 0x7);
STATIC_ASSERT(right_n_bits(1|2) == 0x7);

// Check for Flush-To-Zero mode

// On some processors faster execution can be achieved by setting a
// mode to return zero for extremely small results, rather than an
// IEEE-754 subnormal number. This mode is not compatible with the
// Java Language Standard.

// We need the addition of _large_subnormal and _small_subnormal to be
// performed at runtime. _small_subnormal is volatile so that
// expressions involving it cannot be evaluated at compile time.
static const double large_subnormal_double
  = jdouble_cast(0x0030000000000000); // 0x1.0p-1020;
static const volatile double small_subnormal_double
  = jdouble_cast(0x0000000000000003); // 0x0.0000000000003p-1022;

// Quickly test to make sure IEEE-754 subnormal numbers are correctly
// handled.
bool IEEE_subnormal_handling_OK() {
  // _small_subnormal is the smallest subnormal number that has two
  // bits set. _large_subnormal is a number such that, when
  // _small_subnormal is added to it, must be rounded according to the
  // mode. These two tests detect the rounding mode in use. If
  // subnormals are turned off (i.e. subnormals-are-zero) flush-to-
  // zero mode is in use.

  return (large_subnormal_double + small_subnormal_double > large_subnormal_double
          && -large_subnormal_double - small_subnormal_double < -large_subnormal_double);
}
