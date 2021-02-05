/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "threadHelper.inline.hpp"
#include "unittest.hpp"

#define TEST_THREAD_COUNT 10 // TODO: update to original value of 30
#define TEST_DURATION 15000 // milliSeconds

// This class have been imported from the original "internal VM test" w/o modification
class TestReservedSpace : AllStatic {
 public:
  static void small_page_write(void* addr, size_t size) {
    size_t page_size = os::vm_page_size();

    char* end = (char*)addr + size;
    for (char* p = (char*)addr; p < end; p += page_size) {
      *p = 1;
    }
  }

  static void release_memory_for_test(ReservedSpace rs) {
    if (rs.special()) {
      guarantee(os::release_memory_special(rs.base(), rs.size()), "Shouldn't fail");
    } else {
      guarantee(os::release_memory(rs.base(), rs.size()), "Shouldn't fail");
    }
  }

  static void test_reserved_space1(size_t size, size_t alignment) {
    assert(is_aligned(size, alignment), "Incorrect input parameters");

    ReservedSpace rs(size,          // size
                     alignment,     // alignment
                     UseLargePages, // large
                     (char *)NULL); // requested_address

    assert(rs.base() != NULL, "Must be");
    assert(rs.size() == size, "Must be");

    assert(is_aligned(rs.base(), alignment), "aligned sizes should always give aligned addresses");
    assert(is_aligned(rs.size(), alignment), "aligned sizes should always give aligned addresses");

    if (rs.special()) {
      small_page_write(rs.base(), size);
    }

    release_memory_for_test(rs);
  }

  static void test_reserved_space2(size_t size) {
    assert(is_aligned(size, os::vm_allocation_granularity()), "Must be at least AG aligned");

    ReservedSpace rs(size);

    assert(rs.base() != NULL, "Must be");
    assert(rs.size() == size, "Must be");

    if (rs.special()) {
      small_page_write(rs.base(), size);
    }

    release_memory_for_test(rs);
  }

  static void test_reserved_space3(size_t size, size_t alignment, bool maybe_large) {
    if (size < alignment) {
      // Tests might set -XX:LargePageSizeInBytes=<small pages> and cause unexpected input arguments for this test.
      assert((size_t)os::vm_page_size() == os::large_page_size(), "Test needs further refinement");
      return;
    }

    assert(is_aligned(size, os::vm_allocation_granularity()), "Must be at least AG aligned");
    assert(is_aligned(size, alignment), "Must be at least aligned against alignment");

    bool large = maybe_large && UseLargePages && size >= os::large_page_size();

    ReservedSpace rs(size, alignment, large);

    assert(rs.base() != NULL, "Must be");
    assert(rs.size() == size, "Must be");

    if (rs.special()) {
      small_page_write(rs.base(), size);
    }

    release_memory_for_test(rs);
  }


  static void test_reserved_space1() {
    size_t size = 2 * 1024 * 1024;
    size_t ag   = os::vm_allocation_granularity();

    test_reserved_space1(size,      ag);
    test_reserved_space1(size * 2,  ag);
    test_reserved_space1(size * 10, ag);
  }

  static void test_reserved_space2() {
    size_t size = 2 * 1024 * 1024;
    size_t ag = os::vm_allocation_granularity();

    test_reserved_space2(size * 1);
    test_reserved_space2(size * 2);
    test_reserved_space2(size * 10);
    test_reserved_space2(ag);
    test_reserved_space2(size - ag);
    test_reserved_space2(size);
    test_reserved_space2(size + ag);
    test_reserved_space2(size * 2);
    test_reserved_space2(size * 2 - ag);
    test_reserved_space2(size * 2 + ag);
    test_reserved_space2(size * 3);
    test_reserved_space2(size * 3 - ag);
    test_reserved_space2(size * 3 + ag);
    test_reserved_space2(size * 10);
    test_reserved_space2(size * 10 + size / 2);
  }

  static void test_reserved_space3() {
    size_t ag = os::vm_allocation_granularity();

    test_reserved_space3(ag,      ag    , false);
    test_reserved_space3(ag * 2,  ag    , false);
    test_reserved_space3(ag * 3,  ag    , false);
    test_reserved_space3(ag * 2,  ag * 2, false);
    test_reserved_space3(ag * 4,  ag * 2, false);
    test_reserved_space3(ag * 8,  ag * 2, false);
    test_reserved_space3(ag * 4,  ag * 4, false);
    test_reserved_space3(ag * 8,  ag * 4, false);
    test_reserved_space3(ag * 16, ag * 4, false);

    if (UseLargePages) {
      size_t lp = os::large_page_size();

      // Without large pages
      test_reserved_space3(lp,     ag * 4, false);
      test_reserved_space3(lp * 2, ag * 4, false);
      test_reserved_space3(lp * 4, ag * 4, false);
      test_reserved_space3(lp,     lp    , false);
      test_reserved_space3(lp * 2, lp    , false);
      test_reserved_space3(lp * 3, lp    , false);
      test_reserved_space3(lp * 2, lp * 2, false);
      test_reserved_space3(lp * 4, lp * 2, false);
      test_reserved_space3(lp * 8, lp * 2, false);

      // With large pages
      test_reserved_space3(lp, ag * 4    , true);
      test_reserved_space3(lp * 2, ag * 4, true);
      test_reserved_space3(lp * 4, ag * 4, true);
      test_reserved_space3(lp, lp        , true);
      test_reserved_space3(lp * 2, lp    , true);
      test_reserved_space3(lp * 3, lp    , true);
      test_reserved_space3(lp * 2, lp * 2, true);
      test_reserved_space3(lp * 4, lp * 2, true);
      test_reserved_space3(lp * 8, lp * 2, true);
    }
  }

  static void test_reserved_space() {
    test_reserved_space1();
    test_reserved_space2();
    test_reserved_space3();
  }
};


class UnitTestThread : public JavaTestThread {
public:
  UnitTestThread(Semaphore* post) : JavaTestThread(post) {}
  virtual ~UnitTestThread() {}

  // Override JavaTestThread's main_run(), this is the code for thread to execute
  void main_run() {
    // tty->print_cr("Starting test thread");
    long stopTime = os::javaTimeMillis() + TEST_DURATION;
    while (os::javaTimeMillis() < stopTime) {
      run_unit_test();
    }
  }

private:
  void run_unit_test() {
    TestReservedSpace::test_reserved_space();
  }
};


TEST_VM(ReservedSpaceConcurrent, test_concurrent_threads) {
    Semaphore done(0);

    UnitTestThread* st[TEST_THREAD_COUNT];
    for (int i = 0; i < TEST_THREAD_COUNT; i++) {
      st[i] = new UnitTestThread(&done);
    }

    for (int i = 0; i < TEST_THREAD_COUNT; i++) {
      st[i]->doit();
    }

    for (int i = 0; i < TEST_THREAD_COUNT; i++) {
      done.wait();
    }
}
