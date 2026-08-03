/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/archiveUtils.hpp"
#include "runtime/atomic.hpp"
#include "unittest.hpp"

class TestArchiveWorkerTask : public ArchiveWorkerTask {
private:
  Atomic<int> _sum;
  Atomic<int> _max;
public:
  TestArchiveWorkerTask() : ArchiveWorkerTask("Test"), _sum(0), _max(0) {}
  void work(int chunk, int max_chunks) override {
    _sum.add_then_fetch(chunk);
    _max.store_relaxed(max_chunks);
  }
  int sum() { return _sum.load_relaxed(); }
  int max() { return _max.load_relaxed(); }
};

// Test a repeated cycle of workers init/shutdown without task works.
TEST_VM(ArchiveWorkersTest, continuous_restart) {
  for (int c = 0; c < 1000; c++) {
    ArchiveWorkers workers;
  }
}

// Test a repeated cycle of sample task works.
TEST_VM(ArchiveWorkersTest, single_task) {
  for (int c = 0; c < 1000; c++) {
    TestArchiveWorkerTask task;
    ArchiveWorkers workers;
    workers.run_task(&task);
    ASSERT_EQ(task.max() * (task.max() - 1) / 2, task.sum());
  }
}

// Test that reusing the workers fails.
#ifdef ASSERT
TEST_VM_ASSERT_MSG(ArchiveWorkersTest, multiple_tasks, ".* Should be unused yet") {
  TestArchiveWorkerTask task;
  ArchiveWorkers workers;
  workers.run_task(&task);
  workers.run_task(&task);
}
#endif // ASSERT
