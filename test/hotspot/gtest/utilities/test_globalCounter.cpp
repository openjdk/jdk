/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "threadHelper.inline.hpp"
#include "utilities/globalCounter.inline.hpp"

constexpr const int good_value = 1337;
constexpr const int bad_value =  4711;

struct TestData {
  long test_value;
};

TEST_VM(GlobalCounter, critical_section) {
  constexpr int number_of_readers = 4;
  volatile bool rt_exit = false;
  Semaphore wrt_start;
  volatile TestData* test = nullptr;

  auto rcu_reader = [&](Thread* current, int _id) {
    volatile TestData** _test = &test;
    wrt_start.signal();
    while (!rt_exit) {
      GlobalCounter::CSContext cs_context = GlobalCounter::critical_section_begin(current);
      volatile TestData* read_test = Atomic::load_acquire(_test);
      long value = Atomic::load_acquire(&read_test->test_value);
      ASSERT_EQ(value, good_value);
      GlobalCounter::critical_section_end(current, cs_context);
      {
        GlobalCounter::CriticalSection cs(current);
        volatile TestData* test = Atomic::load_acquire(_test);
        long value = Atomic::load_acquire(&test->test_value);
        ASSERT_EQ(value, good_value);
      }
    }
  };

  TestThreadGroup<decltype(rcu_reader)> ttg(rcu_reader, number_of_readers);

  TestData* tmp = new TestData();
  tmp->test_value = good_value;
  Atomic::release_store(&test, tmp);
  rt_exit = false;
  ttg.doit();
  int nw = number_of_readers;
  while (nw > 0) {
    wrt_start.wait();
    --nw;
  }
  jlong stop_ms = os::javaTimeMillis() + 1000; // 1 seconds max test time
  for (int i = 0; i < 100000 && stop_ms > os::javaTimeMillis(); i++) {
    volatile TestData* free_tmp = test;
    tmp = new TestData();
    tmp->test_value = good_value;
    Atomic::release_store(&test, tmp);
    GlobalCounter::write_synchronize();
    free_tmp->test_value = bad_value;
    delete free_tmp;
  }
  rt_exit = true;
  ttg.join();
}
