/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/partialArrayTaskStepper.inline.hpp"
#include "unittest.hpp"

static void run_test(size_t length, size_t chunk_size, uint n_workers) {
  const PartialArrayTaskStepper stepper(n_workers);
  size_t index = length % chunk_size;
  uint pending = index < length ? 1u : 0u;
  uint tasks = 0;
  while (pending > 0) {
    ASSERT_LT(index, length);
    --pending;
    pending += stepper.continuation_tasks(index, length, chunk_size);
    index += chunk_size;
    ++tasks;
    ASSERT_LE(pending, n_workers);
    ASSERT_LE(pending, (length - index) / chunk_size);
  }
  ASSERT_EQ(length, index);
  ASSERT_EQ(tasks, length / chunk_size);
}

TEST(PartialArrayTaskStepperTest, coverage_and_task_limit) {
  for (size_t chunk_size = 50; chunk_size <= 500; chunk_size += 50) {
    for (uint n_workers = 1; n_workers <= 256; n_workers = (n_workers * 3 / 2 + 1)) {
      for (size_t length = 0; length <= 1000000; length = (length * 2 + 1)) {
        run_test(length, chunk_size, n_workers);
      }
      // Ensure we hit boundary cases for length % chunk_size == 0.
      for (uint i = 0; i < 2 * n_workers; ++i) {
        run_test(i * chunk_size, chunk_size, n_workers);
      }
    }
  }
}

// Verifies that with below constants the stepper won't create tasks beyond the original array.
TEST(PartialArrayTaskStepperTest, overflow_beyond_array) {
  const size_t Length = INT32_MAX;
  const size_t ChunkSize = 1;
  const size_t Index = 1431655765;
  const uint NumWorkers = 16; // Fanout is 4.

  const PartialArrayTaskStepper stepper(NumWorkers);

  ASSERT_EQ(1u, stepper.continuation_tasks(Index, Length, ChunkSize));
}
