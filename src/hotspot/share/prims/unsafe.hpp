/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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


#ifndef SHARE_PRIMS_UNSAFE_HPP
#define SHARE_PRIMS_UNSAFE_HPP

#include "jni.h"

extern "C" {
  void JNICALL JVM_RegisterJDKInternalMiscUnsafeMethods(JNIEnv *env, jclass unsafecls);
}

jlong Unsafe_field_offset_to_byte_offset(jlong field_offset);

jlong Unsafe_field_offset_from_byte_offset(jlong byte_offset);

// The low three bits of the 8 primitive BasicType values encode size.
constexpr int UNSAFE_PRIMITIVE_SIZE_MASK = 3;

// These are defined as byte constants in the Unsafe class.
// They are used as leading arguments to Unsafe.getReferenceMO, etc.
#define UNSAFE_MEMORY_ORDERS_DO(fn)             \
  fn(MO_PLAIN,      1)                          \
  fn(MO_VOLATILE,   2)                          \
  fn(MO_ACQUIRE,    4)                          \
  fn(MO_RELEASE,    8)                          \
  fn(MO_WEAK_CAS,  16)                          \
  fn(MO_UNALIGNED, 32)                          \
  fn(MO_OPAQUE,     3) /*plain+voltile*/        \
  /*end*/

enum UnsafeMemoryOrder {
  #define UNSAFE_MEMORY_ORDER_DEFINE(mo, code) \
    UNSAFE_ ## mo = code,
  UNSAFE_MEMORY_ORDERS_DO(UNSAFE_MEMORY_ORDER_DEFINE)
  #undef UNSAFE_MEMORY_ORDER_DEFINE
  UNSAFE_MO_MODE_MASK = UNSAFE_MO_PLAIN|UNSAFE_MO_VOLATILE|UNSAFE_MO_ACQUIRE|UNSAFE_MO_RELEASE,
};

// These are defined as byte constants in the Unsafe class.
// They are used only by Unsafe.getAndOperatePrimitiveBitsMO.
#define UNSAFE_PRIMITIVE_BITS_OPERATIONS_DO(fn) \
  fn(OP_ADD,     '+')                           \
  fn(OP_BITAND,  '&')                           \
  fn(OP_BITOR,   '|')                           \
  fn(OP_BITXOR,  '^')                           \
  fn(OP_SWAP,    '=')                           \
  /*end*/

enum UnsafePrimitiveBitsOperation {
  #define UNSAFE_PRIMITIVE_BITS_OPERATION_DEFINE(op, code) \
    UNSAFE_ ## op = code,
  UNSAFE_PRIMITIVE_BITS_OPERATIONS_DO(UNSAFE_PRIMITIVE_BITS_OPERATION_DEFINE)
  #undef UNSAFE_PRIMITIVE_BITS_OPERATION_DEFINE
};
// The high level of macro-abstraction is intended to assist us in
// validating that the constants are the same in Java as in C++.

#endif // SHARE_PRIMS_UNSAFE_HPP
