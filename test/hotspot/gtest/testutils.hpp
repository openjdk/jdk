/*
 * Copyright (c) 2021, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef TESTUTILS_HPP
#define TESTUTILS_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"

class GtestUtils : public AllStatic {
public:

  // Fill range with a byte mark.
  // Tolerates p == NULL or s == 0.
  static void mark_range_with(void* p, size_t s, uint8_t mark);

  // Given a memory range, check that the whole range is filled with the expected byte.
  // If not, hex dump around first non-matching address and return false.
  // If p == NULL or size == 0, returns true.
  static bool is_range_marked(const void* p, size_t s, uint8_t expected);

  // Convenience method with a predefined byte mark.
  static void mark_range(void* p, size_t s)               { mark_range_with(p, s, 32); }
  static bool is_range_marked(const void* p, size_t s)    { return is_range_marked(p, s, 32); }

};

#define ASSERT_RANGE_IS_MARKED_WITH(p, size, mark)  ASSERT_TRUE(GtestUtils::is_range_marked(p, size, mark))
#define ASSERT_RANGE_IS_MARKED(p, size)             ASSERT_TRUE(GtestUtils::is_range_marked(p, size))
#define EXPECT_RANGE_IS_MARKED_WITH(p, size, mark)  EXPECT_TRUE(GtestUtils::is_range_marked(p, size, mark))
#define EXPECT_RANGE_IS_MARKED(p, size)             EXPECT_TRUE(GtestUtils::is_range_marked(p, size))

// Mimicking the official ASSERT_xx and EXPECT_xx counterparts of the googletest suite.
// (ASSERT|EXPECT)_NOT_NULL: check that the given pointer is not NULL
// (ASSERT|EXPECT)_NULL: check that the given pointer is NULL
#define ASSERT_NOT_NULL(p)  ASSERT_NE(p2i(p), 0)
#define ASSERT_NULL(p)      ASSERT_EQ(p2i(p), 0)
#define EXPECT_NOT_NULL(p)  EXPECT_NE(p2i(p), 0)
#define EXPECT_NULL(p)      EXPECT_EQ(p2i(p), 0)

#define ASSERT_ALIGN(p, n) ASSERT_TRUE(is_aligned(p, n))

#ifdef LOG_PLEASE
#define LOG_HERE(s, ...) { printf(s, __VA_ARGS__); printf("\n"); fflush(stdout); }
#else
#define LOG_HERE(s, ...)
#endif

// handy for error analysis
#define PING { printf("%s:%d\n", __FILE__, __LINE__); fflush(stdout); }

#endif // TESTUTILS_HPP
