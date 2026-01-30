/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cppstdlib/limits.hpp"
#include "cppstdlib/type_traits.hpp"
#include "utilities/integerCast.hpp"
#include "utilities/globalDefinitions.hpp"

#include "unittest.hpp"

// Enable gcc warnings to verify we don't get any of these.
// Eventually we plan to have these globally enabled, but not there yet.
#ifdef __GNUC__
#pragma GCC diagnostic warning "-Wconversion"
#pragma GCC diagnostic warning "-Wsign-conversion"
#endif

// Tautology tests for signed -> signed types.
static_assert(is_always_integer_convertible<int32_t, int32_t>());
static_assert(!is_always_integer_convertible<int64_t, int32_t>());
static_assert(is_always_integer_convertible<int32_t, int64_t>());
static_assert(is_always_integer_convertible<int64_t, int64_t>());

// Tautology tests for unsigned -> unsigned types.
static_assert(is_always_integer_convertible<uint32_t, uint32_t>());
static_assert(!is_always_integer_convertible<uint64_t, uint32_t>());
static_assert(is_always_integer_convertible<uint32_t, uint64_t>());
static_assert(is_always_integer_convertible<uint64_t, uint64_t>());

// Tautology tests for signed -> unsigned types.
static_assert(!is_always_integer_convertible<int32_t, uint32_t>());
static_assert(!is_always_integer_convertible<int64_t, uint32_t>());
static_assert(!is_always_integer_convertible<int32_t, uint64_t>());
static_assert(!is_always_integer_convertible<int64_t, uint64_t>());

// Tautology tests for unsigned -> signed types.
static_assert(!is_always_integer_convertible<uint32_t, int32_t>());
static_assert(!is_always_integer_convertible<uint64_t, int32_t>());
static_assert(is_always_integer_convertible<uint32_t, int64_t>());
static_assert(!is_always_integer_convertible<uint64_t, int64_t>());

template<typename T>
struct TestIntegerCastValues {
  static TestIntegerCastValues values;

  T minus_one = static_cast<T>(-1);
  T zero = static_cast<T>(0);
  T one = static_cast<T>(1);
  T min = std::numeric_limits<T>::min();
  T max = std::numeric_limits<T>::max();
};

template<typename T>
TestIntegerCastValues<T> TestIntegerCastValues<T>::values{};

template<typename To, typename From>
struct TestIntegerCastPairedValues {
  static TestIntegerCastPairedValues values;

  From min = static_cast<From>(std::numeric_limits<To>::min());
  From max = static_cast<From>(std::numeric_limits<To>::max());
};

template<typename To, typename From>
TestIntegerCastPairedValues<To, From>
TestIntegerCastPairedValues<To, From>::values{};

//////////////////////////////////////////////////////////////////////////////
// Integer casts between integral types of different sizes.
// Test narrowing to verify checking.
// Test widening to verify no compiler warnings for tautological comparisons.

template<typename To, typename From>
struct TestIntegerCastIntegerValues {
  static TestIntegerCastIntegerValues values;

  TestIntegerCastValues<To> to;
  TestIntegerCastValues<From> from;
  TestIntegerCastPairedValues<To, From> to_as_from;
};

template<typename To, typename From>
TestIntegerCastIntegerValues<To, From>
TestIntegerCastIntegerValues<To, From>::values{};

// signed -> signed is tautological unless From is wider than To.

TEST(TestIntegerCast, wide_signed_to_narrow_signed_integers) {
  using To = int32_t;
  using From = int64_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  EXPECT_TRUE(is_integer_convertible<To>(values.from.minus_one));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.zero));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.one));
  EXPECT_FALSE(is_integer_convertible<To>(values.from.min));
  EXPECT_FALSE(is_integer_convertible<To>(values.from.max));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.min));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.max));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.min - 1));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.max + 1));
}

// unsigned -> unsigned is tautological unless From is wider than To.

TEST(TestIntegerCast, wide_unsigned_to_narrow_unsigned_integers) {
  using To = uint32_t;
  using From = uint64_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  EXPECT_FALSE(is_integer_convertible<To>(values.from.minus_one));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.zero));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.one));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.min));
  EXPECT_FALSE(is_integer_convertible<To>(values.from.max));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.min));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.min));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.min - 1));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.max + 1));
}

TEST(TestIntegerCast, unsigned_to_signed_same_size_integers) {
  using To = int32_t;
  using From = uint32_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  EXPECT_TRUE(is_integer_convertible<To>(values.from.zero));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.one));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.min));
  EXPECT_FALSE(is_integer_convertible<To>(values.from.max));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.min));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.max));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.max + 1));
}

// Narrow unsigned to wide signed is tautological.

TEST(TestIntegerCast, wide_unsigned_to_narrow_signed_integers) {
  using To = int32_t;
  using From = uint64_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  EXPECT_FALSE(is_integer_convertible<To>(values.from.minus_one));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.zero));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.one));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.min));
  EXPECT_FALSE(is_integer_convertible<To>(values.from.max));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.min));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.max));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.min - 1));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.max + 1));
}

TEST(TestIntegerCast, signed_to_unsigned_same_size_integers) {
  using To = uint32_t;
  using From = int32_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  EXPECT_FALSE(is_integer_convertible<To>(values.from.minus_one));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.zero));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.one));
  EXPECT_FALSE(is_integer_convertible<To>(values.from.min));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.max));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.min));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.max));
}

TEST(TestIntegerCast, narrow_signed_to_wide_unsigned_integers) {
  using To = uint64_t;
  using From = int32_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  EXPECT_FALSE(is_integer_convertible<To>(values.from.minus_one));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.zero));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.one));
  EXPECT_FALSE(is_integer_convertible<To>(values.from.min));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.max));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.min));
  EXPECT_FALSE(is_integer_convertible<To>(values.to_as_from.max));
}

TEST(TestIntegerCast, wide_signed_to_narrow_unsigned_integers) {
  using To = uint32_t;
  using From = int64_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  EXPECT_FALSE(is_integer_convertible<To>(values.from.minus_one));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.zero));
  EXPECT_TRUE(is_integer_convertible<To>(values.from.one));
  EXPECT_FALSE(is_integer_convertible<To>(values.from.min));
  EXPECT_FALSE(is_integer_convertible<To>(values.from.max));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.min));
  EXPECT_TRUE(is_integer_convertible<To>(values.to_as_from.max));
}
