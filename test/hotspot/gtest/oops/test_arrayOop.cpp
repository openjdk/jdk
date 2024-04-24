/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "oops/arrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

static bool check_max_length_overflow(BasicType type) {
  julong length = arrayOopDesc::max_array_length(type);
  julong bytes_per_element = type2aelembytes(type);
  julong bytes = length * bytes_per_element
          + arrayOopDesc::base_offset_in_bytes(type);
  return (julong) (size_t) bytes == bytes;
}

TEST_VM(arrayOopDesc, boolean) {
  ASSERT_PRED1(check_max_length_overflow, T_BOOLEAN);
}

TEST_VM(arrayOopDesc, char) {
  ASSERT_PRED1(check_max_length_overflow, T_CHAR);
}

TEST_VM(arrayOopDesc, float) {
  ASSERT_PRED1(check_max_length_overflow, T_FLOAT);
}

TEST_VM(arrayOopDesc, double) {
  ASSERT_PRED1(check_max_length_overflow, T_DOUBLE);
}

TEST_VM(arrayOopDesc, byte) {
  ASSERT_PRED1(check_max_length_overflow, T_BYTE);
}

TEST_VM(arrayOopDesc, short) {
  ASSERT_PRED1(check_max_length_overflow, T_SHORT);
}

TEST_VM(arrayOopDesc, int) {
  ASSERT_PRED1(check_max_length_overflow, T_INT);
}

TEST_VM(arrayOopDesc, long) {
  ASSERT_PRED1(check_max_length_overflow, T_LONG);
}

TEST_VM(arrayOopDesc, object) {
  ASSERT_PRED1(check_max_length_overflow, T_OBJECT);
}

TEST_VM(arrayOopDesc, array) {
  ASSERT_PRED1(check_max_length_overflow, T_ARRAY);
}

TEST_VM(arrayOopDesc, narrowOop) {
  ASSERT_PRED1(check_max_length_overflow, T_NARROWOOP);
}
// T_VOID and T_ADDRESS are not supported by max_array_length()

TEST_VM(arrayOopDesc, base_offset) {
#ifdef _LP64
  if (UseCompressedClassPointers) {
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_BOOLEAN), 16);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_BYTE),    16);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_SHORT),   16);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_CHAR),    16);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_INT),     16);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_FLOAT),   16);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_LONG),    16);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_DOUBLE),  16);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_OBJECT),  16);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_ARRAY),   16);
  } else {
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_BOOLEAN), 20);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_BYTE),    20);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_SHORT),   20);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_CHAR),    20);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_INT),     20);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_FLOAT),   20);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_LONG),    24);
    EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_DOUBLE),  24);
    if (UseCompressedOops) {
      EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_OBJECT), 20);
      EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_ARRAY),  20);
    } else {
      EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_OBJECT), 24);
      EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_ARRAY),  24);
    }
  }
#else
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_BOOLEAN), 12);
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_BYTE),    12);
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_SHORT),   12);
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_CHAR),    12);
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_INT),     12);
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_FLOAT),   12);
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_LONG),    16);
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_DOUBLE),  16);
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_OBJECT),  12);
  EXPECT_EQ(arrayOopDesc::base_offset_in_bytes(T_ARRAY),   12);
#endif
}
