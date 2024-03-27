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

#ifndef SHARE_UTILITIES_BASICTYPES_HPP
#define SHARE_UTILITIES_BASICTYPES_HPP

// Get constants like JVM_T_CHAR and JVM_SIGNATURE_INT, before pulling in <jvm.h>.
#include "classfile_constants.h"

#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#include "utilities/javaTypes.hpp"

typedef unsigned int uint;   NEEDS_CLEANUP

void basic_types_init(); // cannot define here; uses assert


#define SIGNATURE_TYPES_DO(F, N)                \
    F(JVM_SIGNATURE_BOOLEAN, T_BOOLEAN, N)      \
    F(JVM_SIGNATURE_CHAR,    T_CHAR,    N)      \
    F(JVM_SIGNATURE_FLOAT,   T_FLOAT,   N)      \
    F(JVM_SIGNATURE_DOUBLE,  T_DOUBLE,  N)      \
    F(JVM_SIGNATURE_BYTE,    T_BYTE,    N)      \
    F(JVM_SIGNATURE_SHORT,   T_SHORT,   N)      \
    F(JVM_SIGNATURE_INT,     T_INT,     N)      \
    F(JVM_SIGNATURE_LONG,    T_LONG,    N)      \
    F(JVM_SIGNATURE_CLASS,   T_OBJECT,  N)      \
    F(JVM_SIGNATURE_ARRAY,   T_ARRAY,   N)      \
    F(JVM_SIGNATURE_VOID,    T_VOID,    N)      \
    /*end*/

// NOTE: replicated in SA in vm/agent/sun/jvm/hotspot/runtime/BasicType.java
enum BasicType : u1 {
// The values T_BOOLEAN..T_LONG (4..11) are derived from the JVMS.
  T_BOOLEAN     = JVM_T_BOOLEAN,
  T_CHAR        = JVM_T_CHAR,
  T_FLOAT       = JVM_T_FLOAT,
  T_DOUBLE      = JVM_T_DOUBLE,
  T_BYTE        = JVM_T_BYTE,
  T_SHORT       = JVM_T_SHORT,
  T_INT         = JVM_T_INT,
  T_LONG        = JVM_T_LONG,
  // The remaining values are not part of any standard.
  // T_OBJECT and T_VOID denote two more semantic choices
  // for method return values.
  // T_OBJECT and T_ARRAY describe signature syntax.
  // T_ADDRESS, T_METADATA, T_NARROWOOP, T_NARROWKLASS describe
  // internal references within the JVM as if they were Java
  // types in their own right.
  T_OBJECT      = 12,
  T_ARRAY       = 13,
  T_VOID        = 14,
  T_ADDRESS     = 15,
  T_NARROWOOP   = 16,
  T_METADATA    = 17,
  T_NARROWKLASS = 18,
  T_CONFLICT    = 19, // for stack value type with conflicting contents
  T_ILLEGAL     = 99
};

#define SIGNATURE_TYPES_DO(F, N)                \
    F(JVM_SIGNATURE_BOOLEAN, T_BOOLEAN, N)      \
    F(JVM_SIGNATURE_CHAR,    T_CHAR,    N)      \
    F(JVM_SIGNATURE_FLOAT,   T_FLOAT,   N)      \
    F(JVM_SIGNATURE_DOUBLE,  T_DOUBLE,  N)      \
    F(JVM_SIGNATURE_BYTE,    T_BYTE,    N)      \
    F(JVM_SIGNATURE_SHORT,   T_SHORT,   N)      \
    F(JVM_SIGNATURE_INT,     T_INT,     N)      \
    F(JVM_SIGNATURE_LONG,    T_LONG,    N)      \
    F(JVM_SIGNATURE_CLASS,   T_OBJECT,  N)      \
    F(JVM_SIGNATURE_ARRAY,   T_ARRAY,   N)      \
    F(JVM_SIGNATURE_VOID,    T_VOID,    N)      \
    /*end*/

inline bool is_java_type(BasicType t) {
  return T_BOOLEAN <= t && t <= T_VOID;
}

inline bool is_java_primitive(BasicType t) {
  return T_BOOLEAN <= t && t <= T_LONG;
}

inline bool is_subword_type(BasicType t) {
  // these guys are processed exactly like T_INT in calling sequences:
  return (t == T_BOOLEAN || t == T_CHAR || t == T_BYTE || t == T_SHORT);
}

inline bool is_signed_subword_type(BasicType t) {
  return (t == T_BYTE || t == T_SHORT);
}

inline bool is_unsigned_subword_type(BasicType t) {
  return (t == T_BOOLEAN || t == T_CHAR);
}

inline bool is_double_word_type(BasicType t) {
  return (t == T_DOUBLE || t == T_LONG);
}

inline bool is_reference_type(BasicType t, bool include_narrow_oop = false) {
  return (t == T_OBJECT || t == T_ARRAY || (include_narrow_oop && t == T_NARROWOOP));
}

inline bool is_integral_type(BasicType t) {
  return is_subword_type(t) || t == T_INT || t == T_LONG;
}

inline bool is_non_subword_integral_type(BasicType t) {
  return t == T_INT || t == T_LONG;
}

inline bool is_floating_point_type(BasicType t) {
  return (t == T_FLOAT || t == T_DOUBLE);
}

extern char type2char_tab[T_CONFLICT+1];     // Map a BasicType to a jchar
inline char type2char(BasicType t) { return (uint)t < T_CONFLICT+1 ? type2char_tab[t] : 0; }
extern int type2size[T_CONFLICT+1];         // Map BasicType to result stack elements
extern const char* type2name_tab[T_CONFLICT+1];     // Map a BasicType to a char*
extern BasicType name2type(const char* name);

const char* type2name(BasicType t);

inline jlong max_signed_integer(BasicType bt) {
  if (bt == T_INT) {
    return max_jint;
  }
  assert(bt == T_LONG, "unsupported");
  return max_jlong;
}

inline jlong min_signed_integer(BasicType bt) {
  if (bt == T_INT) {
    return min_jint;
  }
  assert(bt == T_LONG, "unsupported");
  return min_jlong;
}

// Auxiliary math routines
// least common multiple
extern size_t lcm(size_t a, size_t b);


// NOTE: replicated in SA in vm/agent/sun/jvm/hotspot/runtime/BasicType.java
enum BasicTypeSize {
  T_BOOLEAN_size     = 1,
  T_CHAR_size        = 1,
  T_FLOAT_size       = 1,
  T_DOUBLE_size      = 2,
  T_BYTE_size        = 1,
  T_SHORT_size       = 1,
  T_INT_size         = 1,
  T_LONG_size        = 2,
  T_OBJECT_size      = 1,
  T_ARRAY_size       = 1,
  T_NARROWOOP_size   = 1,
  T_NARROWKLASS_size = 1,
  T_VOID_size        = 0
};

// this works on valid parameter types but not T_VOID, T_CONFLICT, etc.
inline int parameter_type_word_count(BasicType t) {
  if (is_double_word_type(t))  return 2;
  assert(is_java_primitive(t) || is_reference_type(t), "no goofy types here please");
  assert(type2size[t] == 1, "must be");
  return 1;
}

// maps a BasicType to its instance field storage type:
// all sub-word integral types are widened to T_INT
extern BasicType type2field[T_CONFLICT+1];
extern BasicType type2wfield[T_CONFLICT+1];


// size in bytes
enum ArrayElementSize {
  T_BOOLEAN_aelem_bytes     = 1,
  T_CHAR_aelem_bytes        = 2,
  T_FLOAT_aelem_bytes       = 4,
  T_DOUBLE_aelem_bytes      = 8,
  T_BYTE_aelem_bytes        = 1,
  T_SHORT_aelem_bytes       = 2,
  T_INT_aelem_bytes         = 4,
  T_LONG_aelem_bytes        = 8,
#ifdef _LP64
  T_OBJECT_aelem_bytes      = 8,
  T_ARRAY_aelem_bytes       = 8,
#else
  T_OBJECT_aelem_bytes      = 4,
  T_ARRAY_aelem_bytes       = 4,
#endif
  T_NARROWOOP_aelem_bytes   = 4,
  T_NARROWKLASS_aelem_bytes = 4,
  T_VOID_aelem_bytes        = 0
};

extern int _type2aelembytes[T_CONFLICT+1]; // maps a BasicType to nof bytes used by its array element
#ifdef ASSERT
extern int type2aelembytes(BasicType t, bool allow_address = false); // asserts
#else
inline int type2aelembytes(BasicType t, bool allow_address = false) { return _type2aelembytes[t]; }
#endif

inline bool same_type_or_subword_size(BasicType t1, BasicType t2) {
  return (t1 == t2) || (is_subword_type(t1) && type2aelembytes(t1) == type2aelembytes(t2));
}

#endif //HOTSPOT_BASICTYPES_HPP
