/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "utilities/globalDefinitions.hpp"

#include <stdlib.h>
#include <stdio.h>

#define GTEST_DONT_DEFINE_TEST 1

// googlemock has ::testing::internal::Log function, so we need to temporary
// undefine 'Log' from logging/log.hpp and define it back after gmock header
// file is included. As SS compiler doesn't have push_/pop_macro pragmas and
// log.hpp might have been already included, we have to copy-paste macro definition.
#ifdef Log
  #define UNDEFINED_Log
  #undef Log
#endif

// R macro is defined by src/hotspot/cpu/arm/register_arm.hpp, F$n are defined
// in ppc/register_ppc.hpp, these macros conflict with typenames used in
// internal googlemock templates. As the macros are not expected to be used by
// any of tests directly, and this header file is supposed to be the last
// include, we just undefine it; if/when it changes, we will need to re-define
// the macros after the following includes.
#undef R
#undef F1
#undef F2

#include "utilities/vmassert_uninstall.hpp"
BEGIN_ALLOW_FORBIDDEN_FUNCTIONS
#include "gmock/gmock.h"
#include "gtest/gtest.h"
END_ALLOW_FORBIDDEN_FUNCTIONS
#include "utilities/vmassert_reinstall.hpp"

#ifdef UNDEFINED_Log
  #define Log(...)  LogImpl<LOG_TAGS(__VA_ARGS__)> // copied from logging/log.hpp
  #undef UNDEFINED_Log
#endif

// Wrapper around os::exit so we don't need to include os.hpp here.
extern void gtest_exit_from_child_vm(int num);

#define CONCAT(a, b) a ## b

#define TEST(category, name) GTEST_TEST(category, name)

#define TEST_VM(category, name) GTEST_TEST(category, CONCAT(name, _vm))

#define TEST_VM_F(test_fixture, name)                               \
  GTEST_TEST_(test_fixture, name ## _vm, test_fixture,              \
              ::testing::internal::GetTypeId<test_fixture>())

#define TEST_OTHER_VM(category, name)                               \
  static void test_  ## category ## _ ## name ## _();               \
                                                                    \
  static void child_ ## category ## _ ## name ## _() {              \
    ::testing::GTEST_FLAG(throw_on_failure) = true;                 \
    test_ ## category ## _ ## name ## _();                          \
    JavaVM* jvm[1];                                                 \
    jsize nVMs = 0;                                                 \
    JNI_GetCreatedJavaVMs(&jvm[0], 1, &nVMs);                       \
    if (nVMs == 1) {                                                \
      int ret = jvm[0]->DestroyJavaVM();                            \
      if (ret != 0) {                                               \
        fprintf(stderr, "Warning: DestroyJavaVM error %d\n", ret);  \
      }                                                             \
    }                                                               \
    fprintf(stderr, "OKIDOKI");                                     \
    gtest_exit_from_child_vm(0);                                    \
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
    gtest_exit_from_child_vm(0);                                    \
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
    gtest_exit_from_child_vm(0);                                    \
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

#define TEST_VM_FATAL_ERROR_MSG(category, name, msg)                \
  static void test_  ## category ## _ ## name ## _();               \
                                                                    \
  static void child_ ## category ## _ ## name ## _() {              \
    ::testing::GTEST_FLAG(throw_on_failure) = true;                 \
    test_ ## category ## _ ## name ## _();                          \
    gtest_exit_from_child_vm(0);                                    \
  }                                                                 \
                                                                    \
  TEST(category, CONCAT(name, _vm_assert)) {                        \
    ASSERT_EXIT(child_ ## category ## _ ## name ## _(),             \
                ::testing::ExitedWithCode(1),                       \
                msg);                                               \
  }                                                                 \
                                                                    \
  void test_ ## category ## _ ## name ## _()

#define TEST_VM_CRASH_SIGNAL(category, name, signame)               \
  static void test_  ## category ## _ ## name ## _();               \
                                                                    \
  static void child_ ## category ## _ ## name ## _() {              \
    ::testing::GTEST_FLAG(throw_on_failure) = true;                 \
    test_ ## category ## _ ## name ## _();                          \
    gtest_exit_from_child_vm(0);                                    \
  }                                                                 \
                                                                    \
  TEST(category, CONCAT(name, _vm_assert)) {                        \
    ASSERT_EXIT(child_ ## category ## _ ## name ## _(),             \
                ::testing::ExitedWithCode(1),                       \
                "signaled: " signame);                              \
  }                                                                 \
                                                                    \
  void test_ ## category ## _ ## name ## _()


#endif // UNITTEST_HPP
