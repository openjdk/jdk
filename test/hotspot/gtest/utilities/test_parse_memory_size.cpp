/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 SAP SE. All rights reserved.
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
#include "jvm_io.h"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/parseInteger.hpp"
#include "testutils.hpp"
#include "unittest.hpp"

template <typename T> const char* type_name();
template <> const char* type_name<uint64_t>() { return "uint64_t"; }
template <> const char* type_name<uint32_t>() { return "uint32_t"; }
template <> const char* type_name<int64_t>()  { return "int64_t"; }
template <> const char* type_name<int32_t>()  { return "int32_t"; }

//#define LOG(s, ...) LOG_HERE(s, __VA_ARGS__)
#define LOG(s, ...)

template <typename T>
static void do_test_valid(T expected_value, const char* pattern) {
  LOG("%s: \"%s\", expect: " UINT64_FORMAT "(" UINT64_FORMAT_X ")", type_name<T>(), pattern,
      (uint64_t)expected_value, (uint64_t)expected_value);
  T value = 17;
  char* end = nullptr;

  stringStream ss;
  ss.print_raw(pattern);

  bool rc = parse_integer(ss.base(), &end, &value);
  ASSERT_TRUE(rc);
  ASSERT_EQ(value, expected_value);

  rc = parse_integer(ss.base(), &value);
  ASSERT_TRUE(rc);
  ASSERT_EQ(value, expected_value);

  // Now test with a trailing pattern.
  // parse_memory_size() should return remainder pointer,
  // parse_argument_memory_size() should flatly refuse to parse this.
  ss.print(":-)");
  rc = parse_integer(ss.base(), &end, &value);
  ASSERT_TRUE(rc);
  ASSERT_EQ(value, expected_value);
  ASSERT_EQ(end, ss.base() + strlen(pattern));
  EXPECT_STREQ(end, ":-)");

  rc = parse_integer(ss.base(), &value);
  ASSERT_FALSE(rc);
}

template <typename T>
static void test_valid(T value, bool hex, T scale, const char* unit) {
  if ((std::numeric_limits<T>::max() / scale) >= value) {
    T expected_result = value * scale;
    stringStream ss;
    if (hex) {
      ss.print(UINT64_FORMAT_X "%s", (uint64_t)value, unit);  // e.g. "0xFFFF"
    } else {
      ss.print(UINT64_FORMAT "%s", (uint64_t)value, unit);    // e.g. "65535"
    }
    do_test_valid((T)expected_result, ss.base());
  }
}

template <typename T>
static void test_valid_all_units(T value, bool hex) {
  test_valid(value, hex, (T)1, "");
  test_valid(value, hex, (T)K, "k");
  test_valid(value, hex, (T)K, "K");
  test_valid(value, hex, (T)M, "m");
  test_valid(value, hex, (T)M, "M");
  test_valid(value, hex, (T)G, "g");
  test_valid(value, hex, (T)G, "G");
  if (sizeof(T) > 4) {
    test_valid(value, hex, (T)((uint64_t)G * 1024), "t");
    test_valid(value, hex, (T)((uint64_t)G * 1024), "T");
  }
}

template <typename T>
static void test_valid_all_power_of_twos() {
  for (int hex = 0; hex < 3; hex ++) {
    for (T i = 1; i != 0; i <<= 2) {
      test_valid_all_units(i - 1, hex == 1);
      test_valid_all_units(i, hex == 1);
      test_valid_all_units(i + 1, hex == 1);
    }
  }
}

TEST(ParseMemorySize, positives) {
  test_valid_all_power_of_twos<uint64_t>();
  test_valid_all_power_of_twos<uint32_t>();
  test_valid_all_power_of_twos<int64_t>();
  test_valid_all_power_of_twos<int32_t>();
}

// Test invalids.
// Note that parse_argument_memory_size is more restrictive than parse_memory_size, because
// the latter accepts trailing content.

static void do_test_invalid_both(const char* pattern) {
  uint64_t value = 4711;
  char* end = nullptr;

  LOG("%s\n", pattern);

  bool rc = parse_integer(pattern, &end, &value);
  EXPECT_FALSE(rc);
  rc = parse_integer(pattern, &value);
  EXPECT_FALSE(rc);
}

static void do_test_invalid_for_parse_arguments(const char* pattern) {
  uint64_t value = 4711;
  char* end = nullptr;

  LOG("%s\n", pattern);

  // The first overload parses until unrecognized chars are encountered, then
  // returns pointer to string remainder.
  bool rc = parse_integer(pattern, &end, &value);
  ASSERT_TRUE(rc);
  // The second overload parses everything; unrecognized chars will make it fail.
  rc = parse_integer(pattern, &value);
  ASSERT_FALSE(rc);
}

TEST(ParseMemorySize, negatives_both) {
  do_test_invalid_both("");
  do_test_invalid_both("abc");

  do_test_invalid_for_parse_arguments("100 M"); // parse_memory_size would see "100", parse_argument_memory_size would reject it
  do_test_invalid_for_parse_arguments("100X");  // parse_memory_size would see "100", parse_argument_memory_size would reject it
}
