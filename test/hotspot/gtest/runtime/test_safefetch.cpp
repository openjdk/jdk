/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/safefetch.inline.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/vmError.hpp"
#include "unittest.hpp"

// Note: beyond these tests, there exist additional tests testing that safefetch in error handling
// (in the context of signal handling) works, see runtime/ErrorHandling

static const intptr_t patternN = LP64_ONLY(0xABCDABCDABCDABCDULL) NOT_LP64(0xABCDABCD);
static const int pattern32 = 0xABCDABCD;

static intptr_t* invalid_addressN =   (intptr_t*)VMError::segfault_address;
static int* invalid_address32 =       (int*)VMError::segfault_address;

TEST_VM(os, safefetch_can_use) {
  // Once VM initialization is through,
  // safefetch should work on every platform.
  ASSERT_TRUE(CanUseSafeFetch32());
}

static void test_safefetchN_positive() {
  intptr_t v = patternN;
  intptr_t a = SafeFetchN(&v, 1);
  ASSERT_EQ(patternN, a);
}

static void test_safefetch32_positive() {
  int v[2] = { pattern32, ~pattern32 };
  uint64_t a = SafeFetch32(v, 1);
  ASSERT_EQ((uint64_t)pattern32, a);
}

static void test_safefetchN_negative() {
  // Non-NULL invalid
  intptr_t a = SafeFetchN(invalid_addressN, patternN);
  ASSERT_EQ(patternN, a);
  a = SafeFetchN(invalid_addressN, ~patternN);
  ASSERT_EQ(~patternN, a);

  // Also try NULL
#ifndef AIX
  a = SafeFetchN(nullptr, patternN);
  ASSERT_EQ(patternN, a);
  a = SafeFetchN(nullptr, ~patternN);
  ASSERT_EQ(~patternN, a);
#endif
}

static void test_safefetch32_negative() {
  // Non-NULL invalid
  int a = SafeFetch32(invalid_address32, pattern32);
  ASSERT_EQ(pattern32, a);
  a = SafeFetch32(invalid_address32, ~pattern32);
  ASSERT_EQ(~pattern32, a);

  // Also try NULL
#ifndef AIX
  a = SafeFetch32(nullptr, pattern32);
  ASSERT_EQ(pattern32, a);
  a = SafeFetch32(nullptr, ~pattern32);
  ASSERT_EQ(~pattern32, a);
#endif
}

TEST_VM(os, safefetchN_positive) {
  test_safefetchN_positive();
}

TEST_VM(os, safefetch32_positive) {
  test_safefetch32_positive();
}

TEST_VM(os, safefetchN_negative) {
  test_safefetchN_negative();
}

TEST_VM(os, safefetch32_negative) {
  test_safefetch32_negative();
}

// Try with Thread::current being NULL. SafeFetch should work then too.
// See JDK-8282475

class ThreadCurrentNullMark : public StackObj {
  Thread* _saved;
public:
  ThreadCurrentNullMark() {
    _saved = Thread::current();
    Thread::clear_thread_current();
  }
  ~ThreadCurrentNullMark() {
    _saved->initialize_thread_current();
  }
};

TEST_VM(os, safefetchN_positive_thread_current_null) {
  ThreadCurrentNullMark tcnm;
  test_safefetchN_positive();
}

TEST_VM(os, safefetch32_positive_thread_current_null) {
  ThreadCurrentNullMark tcnm;
  test_safefetch32_positive();
}

TEST_VM(os, safefetchN_negative_thread_current_null) {
  ThreadCurrentNullMark tcnm;
  test_safefetchN_negative();
}

TEST_VM(os, safefetch32_negative_thread_current_null) {
  ThreadCurrentNullMark tcnm;
  test_safefetch32_negative();
}

class VM_TestSafeFetchAtSafePoint : public VM_GTestExecuteAtSafepoint {
public:
  void doit() {
    // Regression test for JDK-8257828
    // Should not crash.
    test_safefetchN_negative();
  }
};

TEST_VM(os, safefetch_negative_at_safepoint) {
  VM_TestSafeFetchAtSafePoint op;
  ThreadInVMfromNative invm(JavaThread::current());
  VMThread::execute(&op);
}
