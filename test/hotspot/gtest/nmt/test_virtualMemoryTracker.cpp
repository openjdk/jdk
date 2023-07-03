/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "services/memTracker.hpp"
#include "services/virtualMemoryTracker.hpp"
#include "unittest.hpp"

static size_t get_committed() {
  VirtualMemorySnapshot snapshot;
  VirtualMemorySummary::snapshot(&snapshot);

  return snapshot.by_type(mtThreadStack)->committed();
}

TEST_VM(VirtualMemoryTracker, missing_remove_released_region) {
  if (!MemTracker::enabled()) {
    return;
  }

  // Simulate the case where we miss the ending of a thread.
  for (int i = 100; i >= 0; --i) {
    size_t size = 1024 * 1024;
    NativeCallStack empty_stack;

    // Get a region of mapped memory not tracked by the virtual memory tracker.
    address base = (address) os::reserve_memory(2 * size, false);
    VirtualMemoryTracker::remove_released_region(base, 2 * size);
    size_t init_sz = get_committed();

    // Reserve and commit everything. We have to, since getting the snapshot 'detects'
    // committed but not reported memory for thread stacks and the detection will not work
    // on MacOSX (not implemented).
    VirtualMemoryTracker::add_reserved_region(base, size, empty_stack, mtThreadStack);
    VirtualMemoryTracker::add_committed_region(base, size, empty_stack);

    // Now pretend we have forgotten to call remove_released_region and allocate an new
    // overlapping region with some commited memory.
    VirtualMemoryTracker::add_reserved_region(base + size / 2, size, empty_stack, mtThreadStack);
    VirtualMemoryTracker::add_committed_region(base + size / 2, size, empty_stack);

    // And remove some of the the committed memory again by reserving a partially overlappinbg region.
    VirtualMemoryTracker::add_reserved_region(base, size, empty_stack, mtThreadStack);
    VirtualMemoryTracker::add_committed_region(base, size, empty_stack);
    size_t new_sz = get_committed();

    // Give back the memory.
    VirtualMemoryTracker::remove_released_region(base, size);
    VirtualMemoryTracker::add_reserved_region(base, 2 * size, empty_stack, mtThreadStack);
    os::release_memory((char*) base, 2 * size);

    // If a parallel thread committed memory concurrently, we get a wrong test result.
    // This should not happen often, so try a few times.
    if (new_sz - size == init_sz) {
      break;
    }

    // If it fails too often log the values we see.
    if (i < 50) {
      tty->print_cr("init_sz: %d, new_sz %d, diff %d, region_size %d", (int) init_sz, (int) new_sz, (int) (new_sz - init_sz), (int) size);
    }

    // Trigger a test failure on the last run.
    if (i == 0) {
      EXPECT_TRUE(new_sz - size == init_sz) << "new_sz: " << new_sz << ", init_sz: " << init_sz <<
                                               ", diff: " << (new_sz - init_sz) << ", region size: " << size;
    }
  }
}
