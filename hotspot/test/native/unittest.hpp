/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef UNITTEST_HPP
#define UNITTEST_HPP

#include <stdlib.h>
#include <stdio.h>

#define GTEST_DONT_DEFINE_TEST 1
#include "gtest/gtest.h"

// gtest/gtest.h includes assert.h which will define the assert macro, but hotspot has its
// own standards incompatible assert macro that takes two parameters.
// The workaround is to undef assert and then re-define it. The re-definition
// must unfortunately be copied since debug.hpp might already have been
// included and a second include wouldn't work due to the header guards in debug.hpp.
#ifdef assert
  #undef assert
  #ifdef vmassert
    #define assert(p, ...) vmassert(p, __VA_ARGS__)
  #endif
#endif

#define CONCAT(a, b) a ## b

#define TEST(category, name) GTEST_TEST(category, CONCAT(name, _test))

#define TEST_VM(category, name) GTEST_TEST(category, CONCAT(name, _test_vm))

#define TEST_VM_F(test_fixture, name)                               \
  GTEST_TEST_(test_fixture, name ## _test_vm, test_fixture,         \
              ::testing::internal::GetTypeId<test_fixture>())

#define TEST_OTHER_VM(category, name)                               \
  static void test_  ## category ## _ ## name ## _();               \
                                                                    \
  static void child_ ## category ## _ ## name ## _() {              \
    ::testing::GTEST_FLAG(throw_on_failure) = true;                 \
    test_ ## category ## _ ## name ## _();                          \
    fprintf(stderr, "OKIDOKI");                                     \
    exit(0);                                                        \
  }                                                                 \
                                                                    \
  TEST(category, CONCAT(name, _other_vm)) {                         \
    ASSERT_EXIT(child_ ## category ## _ ## name ## _(),             \
                ::testing::ExitedWithCode(0),                       \
                ".*OKIDOKI.*");                                     \
  }                                                                 \
                                                                    \
  void test_ ## category ## _ ## name ## _()

#ifdef ASSERT
#define TEST_VM_ASSERT(category, name)                              \
  static void test_  ## category ## _ ## name ## _();               \
                                                                    \
  static void child_ ## category ## _ ## name ## _() {              \
    ::testing::GTEST_FLAG(throw_on_failure) = true;                 \
    test_ ## category ## _ ## name ## _();                          \
    exit(0);                                                        \
  }                                                                 \
                                                                    \
  TEST(category, CONCAT(name, _vm_assert)) {                        \
    ASSERT_EXIT(child_ ## category ## _ ## name ## _(),             \
                ::testing::ExitedWithCode(1),                       \
                "assert failed");                                   \
  }                                                                 \
                                                                    \
  void test_ ## category ## _ ## name ## _()
#else
#define TEST_VM_ASSERT(...)                                         \
    TEST_VM_ASSERT is only available in debug builds
#endif

#ifdef ASSERT
#define TEST_VM_ASSERT_MSG(category, name, msg)                     \
  static void test_  ## category ## _ ## name ## _();               \
                                                                    \
  static void child_ ## category ## _ ## name ## _() {              \
    ::testing::GTEST_FLAG(throw_on_failure) = true;                 \
    test_ ## category ## _ ## name ## _();                          \
    exit(0);                                                        \
  }                                                                 \
                                                                    \
  TEST(category, CONCAT(name, _vm_assert)) {                        \
    ASSERT_EXIT(child_ ## category ## _ ## name ## _(),             \
                ::testing::ExitedWithCode(1),                       \
                "assert failed: " msg);                             \
  }                                                                 \
                                                                    \
  void test_ ## category ## _ ## name ## _()
#else
#define TEST_VM_ASSERT_MSG(...)                                     \
    TEST_VM_ASSERT_MSG is only available in debug builds
#endif

#endif // UNITTEST_HPP
