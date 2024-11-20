/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "cds/archiveUtils.hpp"
#include "unittest.hpp"

class TestArchiveWorkerTask : public ArchiveWorkerTask {
private:
  volatile int _sum;
  int _max;
public:
  TestArchiveWorkerTask() : ArchiveWorkerTask("Test"), _sum(0), _max(0) {}
  void work(int chunk, int max_chunks) override {
    Atomic::add(&_sum, chunk);
    Atomic::store(&_max, max_chunks);
  }
  int sum() { return Atomic::load(&_sum); }
  int max() { return Atomic::load(&_max); }
};

// Test a repeated cycle of pool start works.
TEST_VM(ArchiveWorkersTest, continuous_restart) {
  for (int c = 0; c < 100; c++) {
    ArchiveWorkersUseMark mark;
  }
}

// Test a repeated cycle of single-task-use works.
TEST_VM(ArchiveWorkersTest, single_task) {
  for (int c = 0; c < 100; c++) {
    ArchiveWorkersUseMark mark;
    TestArchiveWorkerTask task;
    ArchiveWorkers::workers()->run_task(&task);
    ASSERT_EQ(task.max() * (task.max() - 1) / 2, task.sum());
  }
}

// Test a repeated cycle of multi-task-use works.
TEST_VM(ArchiveWorkersTest, multiple_tasks) {
  for (int c = 0; c < 100; c++) {
    ArchiveWorkersUseMark mark;
    for (int t = 0; t < 10; t++) {
      TestArchiveWorkerTask task;
      ArchiveWorkers::workers()->run_task(&task);
      ASSERT_EQ(task.max() * (task.max() - 1) / 2, task.sum());
    }
  }
}

// Test that using the pool without use mark fails.
#ifdef ASSERT
TEST_VM_ASSERT_MSG(ArchiveWorkersTest, pool_use_without_mark, ".* Should be ready") {
  TestArchiveWorkerTask task;
  ArchiveWorkers::workers()->run_task(&task);
}

// Test that using the pool outside use mark fails.
TEST_VM_ASSERT_MSG(ArchiveWorkersTest, pool_use_after_mark, ".* Should be ready") {
  {
    ArchiveWorkersUseMark mark;
  }
  TestArchiveWorkerTask task;
  ArchiveWorkers::workers()->run_task(&task);
}
#endif // ASSERT