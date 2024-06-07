/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"
#include <chrono>

using NCSS = NativeCallStackStorage;

class NativeCallStackStorageTest : public testing::Test {};

TEST_VM_F(NativeCallStackStorageTest, DoNotStoreStackIfNotDetailed) {
  NativeCallStack ncs{};
  NCSS ncss(false);
  NCSS::StackIndex si = ncss.push(ncs);
  EXPECT_TRUE(si.is_invalid());
  NativeCallStack ncs_received = ncss.get(si);
  EXPECT_TRUE(ncs_received.is_empty());
}

TEST_VM_F(NativeCallStackStorageTest, CollisionsReceiveDifferentIndexes) {
  constexpr const int nr_of_stacks = 10;
  NativeCallStack ncs_arr[nr_of_stacks];
  for (int i = 0; i < nr_of_stacks; i++) {
    ncs_arr[i] = NativeCallStack((address*)(&i), 1);
  }

  NCSS ncss(true, 1);
  NCSS::StackIndex si_arr[nr_of_stacks];
  for (int i = 0; i < nr_of_stacks; i++) {
    si_arr[i] = ncss.push(ncs_arr[i]);
  }

  // Every SI should be different as every sack is different
  for (int i = 0; i < nr_of_stacks; i++) {
    for (int j = 0; j < nr_of_stacks; j++) {
      if (i == j) continue;
      EXPECT_FALSE(NCSS::StackIndex::equals(si_arr[i],si_arr[j]));
    }
  }
}

TEST_VM_F(NativeCallStackStorageTest, PerfTest) {
  using std::chrono::duration;
  using std::chrono::duration_cast;
  using std::chrono::high_resolution_clock;
  using std::chrono::milliseconds;

  NativeCallStackStorage ncss(true);
  NativeCallStackStorageWithAllocator<CHeapAllocator> ncss_cheap(true);
  NativeCallStackStorageWithAllocator<ArenaAllocator> ncss_arena(true);

  auto make_stack = []() -> NativeCallStack {
    size_t a = os::random();
    size_t b = os::random();
    size_t c = os::random();
    size_t d = os::random();
    address as[4] = {(address)a, (address)b, (address)c, (address)d};
    NativeCallStack stack(as, 4);
    return stack;
  };

  constexpr const int size = 1000000;
  tty->print("Generate stacks... ");
  NativeCallStack* all = NEW_C_HEAP_ARRAY(NativeCallStack, size, mtTest);
  for (int i = 0; i < size; i++) {
    all[i] = make_stack();
  }
  tty->print_cr("Done");

  auto t1 = high_resolution_clock::now();
  for (int i = 0; i < size; i++) {
    ncss.push(all[i]);
  }
  auto t2 = high_resolution_clock::now();

  auto ms_int = duration_cast<milliseconds>(t2 - t1);
  duration<double, std::milli> ms_double = t2 - t1;
  tty->print_cr("Time taken with GrowableArray: %f", ms_double.count());

  t1 = high_resolution_clock::now();
  for (int i = 0; i < size; i++) {
    ncss_cheap.push(all[i]);
  }
  t2 = high_resolution_clock::now();

  ms_int = duration_cast<milliseconds>(t2 - t1);
  ms_double = t2 - t1;
  tty->print_cr("Time taken with CHeap: %f", ms_double.count());

  t1 = high_resolution_clock::now();
  for (int i = 0; i < size; i++) {
    ncss_arena.push(all[i]);
  }
  t2 = high_resolution_clock::now();

  ms_int = duration_cast<milliseconds>(t2 - t1);
  ms_double = t2 - t1;
  tty->print_cr("Time taken with Arena: %f", ms_double.count());

  {
    NativeCallStackStorage ncss(true);
    auto t1 = high_resolution_clock::now();
    for (int i = 0; i < size; i++) {
      ncss.push(all[i]);
    }
    auto t2 = high_resolution_clock::now();

    auto ms_int = duration_cast<milliseconds>(t2 - t1);
    duration<double, std::milli> ms_double = t2 - t1;
    tty->print_cr("Time taken with GrowableArray again: %f", ms_double.count());
  }
}
