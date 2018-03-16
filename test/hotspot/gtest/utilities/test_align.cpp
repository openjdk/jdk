/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/align.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"

#include <limits>

// A few arbitrarily chosen values to test the align functions on.
static uint64_t values[] = {1, 3, 10, 345, 1023, 1024, 1025, 23909034, INT_MAX, uint64_t(-1) / 2, uint64_t(-1) / 2 + 100, uint64_t(-1)};

template <typename T>
static T max_alignment() {
  T max = std::numeric_limits<T>::max();
  return max ^ (max >> 1);
}

#define log(...) SCOPED_TRACE(err_msg(__VA_ARGS__).buffer())

template <typename T, typename A>
static void test_alignments() {
  log("### Test: %c" SIZE_FORMAT " " UINT64_FORMAT " : %c" SIZE_FORMAT " " UINT64_FORMAT " ###\n",
      std::numeric_limits<T>::is_signed ? 's' : 'u', sizeof(T), (uint64_t)std::numeric_limits<T>::max(),
      std::numeric_limits<A>::is_signed ? 's' : 'u', sizeof(A), (uint64_t)std::numeric_limits<A>::max());

  // Test all possible alignment values that fit in type A.
  for (A alignment = max_alignment<A>(); alignment > 0; alignment >>= 1) {
    log("=== Alignment: " UINT64_FORMAT " ===\n", (uint64_t)alignment);

    for (size_t i = 0; i < ARRAY_SIZE(values); i++) {
      log("--- Value: " UINT64_FORMAT "\n", values[i]);

      // Test align up
      const uint64_t up = align_up_(values[i], (uint64_t)alignment);
      if (0 < up && up <= (uint64_t)std::numeric_limits<T>::max()) {
        log("Testing align_up:   alignment: 0x" UINT64_FORMAT_X " value: 0x" UINT64_FORMAT_X " expected: 0x" UINT64_FORMAT_X "\n", (uint64_t)alignment, values[i], up);

        T value = T(values[i]);

        // Check against uint64_t version
        ASSERT_EQ(align_up((uint64_t)value, alignment), up);
        // Check inline function vs macro
        ASSERT_EQ(align_up(value, alignment), align_up_(value, alignment));
        // Sanity check
        ASSERT_GE(align_up(value, alignment), value);
      }

      // Test align down
      const uint64_t down = align_down_(values[i], (uint64_t)alignment);
      if (down <= (uint64_t)std::numeric_limits<T>::max()) {
        log("Testing align_down: alignment: 0x" UINT64_FORMAT_X " value: 0x" UINT64_FORMAT_X " expected: 0x" UINT64_FORMAT_X "\n", (uint64_t)alignment, values[i], down);

        T value = T(values[i]);

        // Check against uint64_t version
        ASSERT_EQ((uint64_t)align_down(value, alignment), down);
        // Check inline function vs macro
        ASSERT_EQ(align_down(value, alignment), align_down_(value, alignment));
        // Sanity check
        ASSERT_LE(align_down(value, alignment), value);
      }

      // Test is aligned
      const bool is = is_aligned_(values[i], (uint64_t)alignment);
      if (values[i] <= (uint64_t)std::numeric_limits<T>::max()) {
        log("Testing is_aligned: alignment: 0x" UINT64_FORMAT_X " value: 0x" UINT64_FORMAT_X " expected: %s\n", (uint64_t)alignment, values[i], is ? "true" : "false");

        T value = T(values[i]);

        // Check against uint64_t version
        ASSERT_EQ(is_aligned(value, alignment), is);
        // Check inline function vs macro
        ASSERT_EQ(is_aligned(value, alignment), is_aligned_(value, alignment));
      }
    }
  }
}

TEST(Align, functions_and_macros) {
  // Test the alignment functions with different type combinations.

  test_alignments<int64_t, uint8_t>();
  test_alignments<int64_t, uint16_t>();
  test_alignments<int64_t, uint32_t>();
  test_alignments<int64_t, int8_t>();
  test_alignments<int64_t, int16_t>();
  test_alignments<int64_t, int32_t>();
  test_alignments<int64_t, int64_t>();

  test_alignments<uint32_t, uint8_t>();
  test_alignments<uint32_t, uint16_t>();
  test_alignments<uint32_t, uint32_t>();
  test_alignments<uint32_t, int8_t>();
  test_alignments<uint32_t, int16_t>();
  test_alignments<uint32_t, int32_t>();

  test_alignments<int32_t, uint8_t>();
  test_alignments<int32_t, uint16_t>();
  test_alignments<int32_t, int8_t>();
  test_alignments<int32_t, int16_t>();
  test_alignments<int32_t, int32_t>();

  test_alignments<uint16_t, uint8_t>();
  test_alignments<uint16_t, uint16_t>();
  test_alignments<uint16_t, int8_t>();
  test_alignments<uint16_t, int16_t>();

  test_alignments<int16_t, uint8_t>();
  test_alignments<int16_t, int8_t>();
  test_alignments<int16_t, int16_t>();

  test_alignments<uint8_t, int8_t>();
  test_alignments<uint8_t, uint8_t>();

  test_alignments<int8_t, int8_t>();
}
