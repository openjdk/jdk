/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allStatic.hpp"
#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include <type_traits>
#include "unittest.hpp"

class EnableIfTest: AllStatic {
  class A: AllStatic {
  public:
    template <bool condition>
    static typename EnableIf<condition, char>::type test();
    template <bool condition>
    static typename EnableIf<!condition, long>::type test();
  };

  static const bool A_test_true_is_char = sizeof(A::test<true>()) == sizeof(char);
  STATIC_ASSERT(A_test_true_is_char);

  static const bool A_test_false_is_long = sizeof(A::test<false>()) == sizeof(long);
  STATIC_ASSERT(A_test_false_is_long);
};

template<typename T, ENABLE_IF(std::is_integral<T>::value)>
static T sub1(T x) { return x - 1; }

template<typename T, ENABLE_IF(std::is_integral<T>::value)>
static T sub2(T x);

template<typename T, ENABLE_IF_PARAM(std::is_integral<T>::value)>
T sub2(T x) { return x - 2; }

TEST(TestEnableIfParam, one_decl_and_def) {
  EXPECT_EQ(15, sub1(16));
}

TEST(TestEnableIfParam, separate_decl_and_def) {
  EXPECT_EQ(14, sub2(16));
}
