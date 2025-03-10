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
 */

#include "runtime/stubRoutines.hpp"
#include "utilities/copy.hpp"
#include "unittest.hpp"

typedef void (*arraycopy_fn)(address src, address dst, int count);

// simple tests of generated arraycopy functions
static void test_arraycopy_func(address func, int alignment) {
  u_char v = 0xcc;
  u_char v2 = 0x11;
  jlong lbuffer[8];
  jlong lbuffer2[8];
  address fbuffer  = (address) lbuffer;
  address fbuffer2 = (address) lbuffer2;
  unsigned int i;
  for (i = 0; i < sizeof(lbuffer); i++) {
    fbuffer[i] = v; fbuffer2[i] = v2;
  }
  // C++ does not guarantee jlong[] array alignment to 8 bytes.
  // Use middle of array to check that memory before it is not modified.
  address buffer  = align_up((address)&lbuffer[4], BytesPerLong);
  address buffer2 = align_up((address)&lbuffer2[4], BytesPerLong);
  // do an aligned copy
  ((arraycopy_fn)func)(buffer, buffer2, 0);
  for (i = 0; i < sizeof(lbuffer); i++) {
    ASSERT_TRUE(fbuffer[i] == v && fbuffer2[i] == v2) << "shouldn't have copied anything";
  }
  // adjust destination alignment
  ((arraycopy_fn)func)(buffer, buffer2 + alignment, 0);
  for (i = 0; i < sizeof(lbuffer); i++) {
    ASSERT_TRUE(fbuffer[i] == v && fbuffer2[i] == v2) << "shouldn't have copied anything";
  }
  // adjust source alignment
  ((arraycopy_fn)func)(buffer + alignment, buffer2, 0);
  for (i = 0; i < sizeof(lbuffer); i++) {
    ASSERT_TRUE(fbuffer[i] == v && fbuffer2[i] == v2) << "shouldn't have copied anything";
  }
}

TEST_VM(StubRoutines, array_copy_routine) {
  MACOS_AARCH64_ONLY(os::current_thread_enable_wx(WXExec));

#define TEST_ARRAYCOPY(type)                                                                  \
  test_arraycopy_func(          StubRoutines::type##_arraycopy(),          sizeof(type));     \
  test_arraycopy_func(          StubRoutines::type##_disjoint_arraycopy(), sizeof(type));     \
  test_arraycopy_func(StubRoutines::arrayof_##type##_arraycopy(),          sizeof(HeapWord)); \
  test_arraycopy_func(StubRoutines::arrayof_##type##_disjoint_arraycopy(), sizeof(HeapWord))

  // Make sure all the arraycopy stubs properly handle zero count
  TEST_ARRAYCOPY(jbyte);
  TEST_ARRAYCOPY(jshort);
  TEST_ARRAYCOPY(jint);
  TEST_ARRAYCOPY(jlong);

#undef TEST_ARRAYCOPY

  MACOS_AARCH64_ONLY(os::current_thread_enable_wx(WXWrite));
}

TEST_VM(StubRoutines, copy_routine) {
  MACOS_AARCH64_ONLY(os::current_thread_enable_wx(WXExec));

#define TEST_COPYRTN(type) \
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::conjoint_##type##s_atomic),  sizeof(type)); \
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::arrayof_conjoint_##type##s), (int)MAX2(sizeof(HeapWord), sizeof(type)))

  // Make sure all the copy runtime routines properly handle zero count
  TEST_COPYRTN(jbyte);
  TEST_COPYRTN(jshort);
  TEST_COPYRTN(jint);
  TEST_COPYRTN(jlong);

#undef TEST_COPYRTN

  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::conjoint_words), sizeof(HeapWord));
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::disjoint_words), sizeof(HeapWord));
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::disjoint_words_atomic), sizeof(HeapWord));
  // Aligned to BytesPerLong
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::aligned_conjoint_words), sizeof(jlong));
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::aligned_disjoint_words), sizeof(jlong));

  MACOS_AARCH64_ONLY(os::current_thread_enable_wx(WXWrite));
}

TEST_VM(StubRoutines, array_fill_routine) {
  MACOS_AARCH64_ONLY(os::current_thread_enable_wx(WXExec));

#define TEST_FILL(type)                                                                      \
  if (StubRoutines::type##_fill() != nullptr) {                         \
    union {                                                                                  \
      double d;                                                                              \
      type body[96];                                                                         \
    } s;                                                                                     \
                                                                                             \
    int v = 32;                                                                              \
    for (int offset = -2; offset <= 2; offset++) {                                           \
      for (int i = 0; i < 96; i++) {                                                         \
        s.body[i] = 1;                                                                       \
      }                                                                                      \
      type* start = s.body + 8 + offset;                                                     \
      for (int aligned = 0; aligned < 2; aligned++) {                                        \
        if (aligned) {                                                                       \
          if (((intptr_t)start) % HeapWordSize == 0) {                                       \
            ((void (*)(type*, int, int))StubRoutines::arrayof_##type##_fill())(start, v, 80); \
          } else {                                                                           \
            continue;                                                                        \
          }                                                                                  \
        } else {                                                                             \
          ((void (*)(type*, int, int))StubRoutines::type##_fill())(start, v, 80); \
        }                                                                                    \
        for (int i = 0; i < 96; i++) {                                                       \
          if (i < (8 + offset) || i >= (88 + offset)) {                                      \
            ASSERT_TRUE(s.body[i] == 1) << "what?";                                          \
          } else {                                                                           \
            ASSERT_TRUE(s.body[i] == 32) << "what?";                                         \
          }                                                                                  \
        }                                                                                    \
      }                                                                                      \
    }                                                                                        \
  }                                                                                          \

  TEST_FILL(jbyte);
  TEST_FILL(jshort);
  TEST_FILL(jint);

#undef TEST_FILL

  MACOS_AARCH64_ONLY(os::current_thread_enable_wx(WXWrite));
}
