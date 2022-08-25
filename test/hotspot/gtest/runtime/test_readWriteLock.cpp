/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/thread.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"
#include "utilities/readWriteLock.hpp"

class ReadWriteLockTest : public ::testing::Test {};

TEST_VM_F(ReadWriteLockTest, WriterLockPreventsReadersFromEnteringCriticalRegion) {
  const int max_iter = 1000;
  int iter = 0;
  ReadWriteLock* mut = new ReadWriteLock();

  volatile bool reader_started = false;
  volatile bool reader_in_critical_region = false;
  volatile bool reader_exited_critical_region = false;

  auto reader = [&](Thread* _current, int _id) {
    Atomic::release_store(&reader_started, true);
    mut->read_lock(Thread::current());
    Atomic::release_store(&reader_in_critical_region, true);
    mut->read_unlock();
    Atomic::release_store(&reader_exited_critical_region, true);
  };

  Semaphore rp{};
  BasicTestThread<decltype(reader)>* rt =
      new BasicTestThread<decltype(reader)>(reader, 0, &rp);

  // 1. Hold write lock
  mut->write_lock(Thread::current());

  // 2. Start reader
  rt->doit();

  // 3. Wait for reader to attempt to lock
  iter = 0;
  while (!Atomic::load_acquire(&reader_started) && iter < max_iter) {
    // Spin, waiting for reader to start up
    iter++;
  }

  // 4. Reader should block, waiting for its turn to enter critical region
  // Check repeatedly to (hopefully) avoid timing issue.
  for (int i = 0; i < max_iter; i++) {
    EXPECT_FALSE(Atomic::load_acquire(&reader_in_critical_region));
  }

  // 5. Let reader enter its critical region
  mut->write_unlock();
  iter = 0;
  while (!Atomic::load_acquire(&reader_in_critical_region) && iter < max_iter) {
    iter++;
  }
  ASSERT_TRUE(Atomic::load_acquire(&reader_in_critical_region));

  // 6. Reader succesfully exits its critical region
  iter = 0;
  while (!Atomic::load_acquire(&reader_exited_critical_region) && iter < max_iter) {
    iter++;
  }
  ASSERT_TRUE(Atomic::load_acquire(&reader_exited_critical_region));
}

TEST_VM_F(ReadWriteLockTest, MultipleReadersAtSameTime) {
  ReadWriteLock* mut = new ReadWriteLock();
  constexpr const int num_readers = 5;
  volatile int concurrent_readers = 0;

  auto r = [&](Thread* _current, int _id) {
    mut->read_lock(Thread::current());
    // Increment counter
    Atomic::add(&concurrent_readers, 1);
    // Don't let go of the lock, exit thread
  };
  TestThreadGroup<decltype(r)> ttg(r, num_readers);
  ttg.doit();
  ttg.join();
  EXPECT_EQ(Atomic::load(&concurrent_readers), num_readers);
  // Unlock for all the threads.
  // Not strictly necessary, but locking looks weird
  // without the corresponding unlock
  for (int i = 0; i < num_readers; i++) {
    mut->read_unlock();
  }
}
