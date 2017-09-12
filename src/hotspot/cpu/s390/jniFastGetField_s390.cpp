/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 SAP SE. All rights reserved.
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

#include "precompiled.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"

// TSO ensures that loads are blocking and ordered with respect to
// to earlier loads, so we don't need LoadLoad membars.

#define __ masm->

#define BUFFER_SIZE 30*sizeof(jint)

address JNI_FastGetField::generate_fast_get_int_field0(BasicType type) {
  // Don't use fast jni accessors.
  return (address) -1;
}

address JNI_FastGetField::generate_fast_get_boolean_field() {
  return generate_fast_get_int_field0(T_BOOLEAN);
}

address JNI_FastGetField::generate_fast_get_byte_field() {
  return generate_fast_get_int_field0(T_BYTE);
}

address JNI_FastGetField::generate_fast_get_char_field() {
  return generate_fast_get_int_field0(T_CHAR);
}

address JNI_FastGetField::generate_fast_get_short_field() {
  return generate_fast_get_int_field0(T_SHORT);
}

address JNI_FastGetField::generate_fast_get_int_field() {
  return generate_fast_get_int_field0(T_INT);
}

address JNI_FastGetField::generate_fast_get_long_field() {
  // Don't use fast jni accessors.
  return (address) -1;
}

address JNI_FastGetField::generate_fast_get_float_field0(BasicType type) {
  // Don't use fast jni accessors.
  return (address) -1;
}

address JNI_FastGetField::generate_fast_get_float_field() {
  return generate_fast_get_float_field0(T_FLOAT);
}

address JNI_FastGetField::generate_fast_get_double_field() {
  return generate_fast_get_float_field0(T_DOUBLE);
}
