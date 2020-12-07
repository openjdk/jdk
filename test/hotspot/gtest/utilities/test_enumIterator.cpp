/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/enumIterator.hpp"
#include <type_traits>
#include "unittest.hpp"

enum class ExplicitTest : int { value1, value2, value3 };
ENUMERATOR_RANGE(ExplicitTest, ExplicitTest::value1, ExplicitTest::value3);
constexpr int explicit_start = 0;
constexpr int explicit_end = 3;

enum class ImplicitTest : int {};
ENUMERATOR_VALUE_RANGE(ImplicitTest, 5, 10);
constexpr int implicit_start = 5;
constexpr int implicit_end = 10;

TEST(TestEnumIterator, explicit_full_range) {
  using Range = EnumRange<ExplicitTest>;
  constexpr Range range{};
  EXPECT_TRUE((std::is_same<ExplicitTest, Range::EnumType>::value));
  EXPECT_EQ(size_t(explicit_end - explicit_start), range.size());
  EXPECT_EQ(ExplicitTest::value1, range.first());
  EXPECT_EQ(ExplicitTest::value3, range.last());
  EXPECT_EQ(size_t(1), range.index(ExplicitTest::value2));
}

TEST(TestEnumIterator, explicit_partial_range) {
  using Range = EnumRange<ExplicitTest>;
  constexpr Range range{ExplicitTest::value2};
  EXPECT_TRUE((std::is_same<ExplicitTest, Range::EnumType>::value));
  EXPECT_EQ(size_t(explicit_end - (explicit_start + 1)), range.size());
  EXPECT_EQ(ExplicitTest::value2, range.first());
  EXPECT_EQ(ExplicitTest::value3, range.last());
  EXPECT_EQ(size_t(0), range.index(ExplicitTest::value2));
}

TEST(TestEnumIterator, implicit_full_range) {
  using Range = EnumRange<ImplicitTest>;
  constexpr Range range{};
  EXPECT_TRUE((std::is_same<ImplicitTest, Range::EnumType>::value));
  EXPECT_EQ(size_t(implicit_end - implicit_start), range.size());
  EXPECT_EQ(static_cast<ImplicitTest>(implicit_start), range.first());
  EXPECT_EQ(static_cast<ImplicitTest>(implicit_end - 1), range.last());
  EXPECT_EQ(size_t(2), range.index(static_cast<ImplicitTest>(implicit_start + 2)));
}

TEST(TestEnumIterator, implicit_partial_range) {
  using Range = EnumRange<ImplicitTest>;
  constexpr Range range{static_cast<ImplicitTest>(implicit_start + 2)};
  EXPECT_TRUE((std::is_same<ImplicitTest, Range::EnumType>::value));
  EXPECT_EQ(size_t(implicit_end - (implicit_start + 2)), range.size());
  EXPECT_EQ(static_cast<ImplicitTest>(implicit_start + 2), range.first());
  EXPECT_EQ(static_cast<ImplicitTest>(implicit_end - 1), range.last());
  EXPECT_EQ(size_t(1), range.index(static_cast<ImplicitTest>(implicit_start + 3)));
}

TEST(TestEnumIterator, explict_iterator) {
  using Range = EnumRange<ExplicitTest>;
  using Iterator = EnumIterator<ExplicitTest>;
  constexpr Range range{};
  EXPECT_EQ(range.first(), *range.begin());
  EXPECT_EQ(Iterator(range.first()), range.begin());
  EnumIterator<ExplicitTest> it = range.begin();
  ++it;
  EXPECT_EQ(ExplicitTest::value2, *it);
  it = range.begin();
  for (int i = explicit_start; i < explicit_end; ++i, ++it) {
    ExplicitTest value = static_cast<ExplicitTest>(i);
    EXPECT_EQ(value, *it);
    EXPECT_EQ(Iterator(value), it);
    EXPECT_EQ(size_t(i - explicit_start), range.index(value));
  }
  EXPECT_EQ(it, range.end());
}

TEST(TestEnumIterator, implicit_iterator) {
  using Range = EnumRange<ImplicitTest>;
  using Iterator = EnumIterator<ImplicitTest>;
  constexpr Range range{};
  EXPECT_EQ(range.first(), *range.begin());
  EXPECT_EQ(Iterator(range.first()), range.begin());
  EnumIterator<ImplicitTest> it = range.begin();
  for (int i = implicit_start; i < implicit_end; ++i, ++it) {
    ImplicitTest value = static_cast<ImplicitTest>(i);
    EXPECT_EQ(value, *it);
    EXPECT_EQ(Iterator(value), it);
    EXPECT_EQ(size_t(i - implicit_start), range.index(value));
  }
  EXPECT_EQ(it, range.end());
}
