/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/safefetch.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/vmError.hpp"
#include "unittest.hpp"
#include "testutils.hpp"

// Note: beyond these tests, there exist additional tests testing that safefetch in error handling
// (in the context of signal handling) works, see runtime/ErrorHandling

static const intptr_t patternN = LP64_ONLY(0xABCDABCDABCDABCDULL) NOT_LP64(0xABCDABCD);
static const int pattern32 = 0xABCDABCD;

static intptr_t* const  bad_addressN = (intptr_t*) VMError::segfault_address;
static int* const       bad_address32 = (int*) VMError::segfault_address;

static intptr_t dataN[3] =  { 0, patternN, 0 };
static int data32[3] = { 0, pattern32, 0 };
static intptr_t* const  good_addressN = dataN + 1;
static int* const       good_address32 = data32 + 1;


void test_safefetchN_positive() {
  intptr_t a = SafeFetchN(good_addressN, 1);
  ASSERT_EQ(patternN, a);
}

static void test_safefetch32_positive() {
  uint64_t a = SafeFetch32(good_address32, 1);
  ASSERT_EQ((uint64_t)pattern32, a);
}

static void test_safefetchN_negative() {
  intptr_t a = SafeFetchN(bad_addressN, 0);
  ASSERT_EQ(0, a);
  a = SafeFetchN(bad_addressN, -1);
  ASSERT_EQ(-1, a);
  a = SafeFetchN(bad_addressN, ~patternN);
  ASSERT_EQ(~patternN, a);
  // Also test nullptr, but not on AIX, where nullptr is readable
#ifndef AIX
  a = SafeFetchN(nullptr, 0);
  ASSERT_EQ(0, a);
  a = SafeFetchN(nullptr, ~patternN);
  ASSERT_EQ(~patternN, a);
#endif
}

static void test_safefetch32_negative() {
  int a = SafeFetch32(bad_address32, 0);
  ASSERT_EQ(0, a);
  a = SafeFetch32(bad_address32, -1);
  ASSERT_EQ(-1, a);
  a = SafeFetch32(bad_address32, ~pattern32);
  ASSERT_EQ(~pattern32, a);
  // Also test nullptr, but not on AIX, where nullptr is readable
#ifndef AIX
  a = SafeFetch32(nullptr, 0);
  ASSERT_EQ(0, a);
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

// Try with Thread::current being nullptr. SafeFetch should work then too.
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

TEST_VM(os, safefetchN_positive_current_null) {
  ThreadCurrentNullMark tcnmark;
  test_safefetchN_positive();
}

TEST_VM(os, safefetch32_positive_current_null) {
  ThreadCurrentNullMark tcnmark;
  test_safefetch32_positive();
}

TEST_VM(os, safefetchN_negative_current_null) {
  ThreadCurrentNullMark tcnmark;
  test_safefetchN_negative();
}

TEST_VM(os, safefetch32_negative_current_null) {
  ThreadCurrentNullMark tcnmark;
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
