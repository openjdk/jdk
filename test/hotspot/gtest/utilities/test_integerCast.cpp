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

template<typename To, typename From>
static void good_integer_conversion(From from) {
  ASSERT_TRUE(is_integer_convertible<To>(from));
  EXPECT_EQ(static_cast<To>(from), integer_cast<To>(from));
}

template<typename To, typename From>
static void bad_integer_conversion(From from) {
  EXPECT_FALSE(is_integer_convertible<To>(from));
}

// signed -> signed is tautological unless From is wider than To.

TEST(TestIntegerCast, wide_signed_to_narrow_signed_integers) {
  using To = int32_t;
  using From = int64_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  good_integer_conversion<To>(values.from.minus_one);
  good_integer_conversion<To>(values.from.zero);
  good_integer_conversion<To>(values.from.one);
  bad_integer_conversion<To>(values.from.min);
  bad_integer_conversion<To>(values.from.max);
  good_integer_conversion<To>(values.to_as_from.min);
  good_integer_conversion<To>(values.to_as_from.max);
  bad_integer_conversion<To>(values.to_as_from.min - 1);
  bad_integer_conversion<To>(values.to_as_from.max + 1);
}

// unsigned -> unsigned is tautological unless From is wider than To.

TEST(TestIntegerCast, wide_unsigned_to_narrow_unsigned_integers) {
  using To = uint32_t;
  using From = uint64_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  bad_integer_conversion<To>(values.from.minus_one);
  good_integer_conversion<To>(values.from.zero);
  good_integer_conversion<To>(values.from.one);
  good_integer_conversion<To>(values.from.min);
  bad_integer_conversion<To>(values.from.max);
  good_integer_conversion<To>(values.to_as_from.min);
  good_integer_conversion<To>(values.to_as_from.min);
  bad_integer_conversion<To>(values.to_as_from.min - 1);
  bad_integer_conversion<To>(values.to_as_from.max + 1);
}

TEST(TestIntegerCast, unsigned_to_signed_same_size_integers) {
  using To = int32_t;
  using From = uint32_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  good_integer_conversion<To>(values.from.zero);
  good_integer_conversion<To>(values.from.one);
  good_integer_conversion<To>(values.from.min);
  bad_integer_conversion<To>(values.from.max);
  bad_integer_conversion<To>(values.to_as_from.min);
  good_integer_conversion<To>(values.to_as_from.max);
  bad_integer_conversion<To>(values.to_as_from.max + 1);
}

// Narrow unsigned to wide signed is tautological.

TEST(TestIntegerCast, wide_unsigned_to_narrow_signed_integers) {
  using To = int32_t;
  using From = uint64_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  bad_integer_conversion<To>(values.from.minus_one);
  good_integer_conversion<To>(values.from.zero);
  good_integer_conversion<To>(values.from.one);
  good_integer_conversion<To>(values.from.min);
  bad_integer_conversion<To>(values.from.max);
  bad_integer_conversion<To>(values.to_as_from.min);
  good_integer_conversion<To>(values.to_as_from.max);
  bad_integer_conversion<To>(values.to_as_from.min - 1);
  bad_integer_conversion<To>(values.to_as_from.max + 1);
}

TEST(TestIntegerCast, signed_to_unsigned_same_size_integers) {
  using To = uint32_t;
  using From = int32_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  bad_integer_conversion<To>(values.from.minus_one);
  good_integer_conversion<To>(values.from.zero);
  good_integer_conversion<To>(values.from.one);
  bad_integer_conversion<To>(values.from.min);
  good_integer_conversion<To>(values.from.max);
  good_integer_conversion<To>(values.to_as_from.min);
  bad_integer_conversion<To>(values.to_as_from.max);
}

TEST(TestIntegerCast, narrow_signed_to_wide_unsigned_integers) {
  using To = uint64_t;
  using From = int32_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  bad_integer_conversion<To>(values.from.minus_one);
  good_integer_conversion<To>(values.from.zero);
  good_integer_conversion<To>(values.from.one);
  bad_integer_conversion<To>(values.from.min);
  good_integer_conversion<To>(values.from.max);
  good_integer_conversion<To>(values.to_as_from.min);
  bad_integer_conversion<To>(values.to_as_from.max);
}

TEST(TestIntegerCast, wide_signed_to_narrow_unsigned_integers) {
  using To = uint32_t;
  using From = int64_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  bad_integer_conversion<To>(values.from.minus_one);
  good_integer_conversion<To>(values.from.zero);
  good_integer_conversion<To>(values.from.one);
  bad_integer_conversion<To>(values.from.min);
  bad_integer_conversion<To>(values.from.max);
  good_integer_conversion<To>(values.to_as_from.min);
  good_integer_conversion<To>(values.to_as_from.max);
}

TEST(TestIntegerCast, permit_tautology) {
  using From = uint32_t;
  using To = int64_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  static_assert(is_always_integer_convertible<From, To>());
  EXPECT_EQ(static_cast<To>(values.from.min),
            (integer_cast<To, true>(values.from.min)));
  EXPECT_EQ(static_cast<To>(values.from.max),
            (integer_cast<To, true>(values.from.max)));
}

TEST(TestIntegerCast, check_constexpr) {
  using From = int64_t;
  using To = int32_t;
  constexpr From value = std::numeric_limits<To>::max();
  constexpr To converted = integer_cast<To>(value);
  EXPECT_EQ(static_cast<To>(value), converted);
}

#ifdef ASSERT

TEST_VM_ASSERT(TestIntegerCast, cast_failure_signed_range) {
  using From = int64_t;
  using To = int32_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  From value = values.from.max;
  To expected = static_cast<To>(value); // Narrowing conversion.
  EXPECT_FALSE(is_integer_convertible<To>(value));
  // Should assert.  If it doesn't, then shuld be equal, so fail.
  EXPECT_NE(static_cast<To>(value), integer_cast<To>(value));
}

TEST_VM_ASSERT(TestIntegerCast, cast_failure_unsigned_range) {
  using From = uint64_t;
  using To = uint32_t;
  using Values = TestIntegerCastIntegerValues<To, From>;
  const Values& values = Values::values;

  From value = values.from.max;
  To expected = static_cast<To>(value); // Narrowing conversion.
  EXPECT_FALSE(is_integer_convertible<To>(value));
  // Should assert.  If it doesn't, then should be equal, so fail.
  EXPECT_NE(static_cast<To>(value), integer_cast<To>(value));
}

#endif // ASSERT
