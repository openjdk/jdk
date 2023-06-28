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

static size_t get_committed(address base, size_t size) {
  VirtualMemorySnapshot snapshot;
  VirtualMemorySummary::snapshot(&snapshot);

  // Assert nothing is physically committed in our test address range.
  address comm_start;
  size_t comm_size;

  if (size > 0) {
    if (!os::committed_in_range(base, size, comm_start, comm_size)) {
      tty->print_cr("Could not get committed region");
    } else if (comm_start != nullptr) {
      tty->print_cr("Got committed region [%p, +%d] in [%p, +%d]", comm_start, (int) comm_size, base, (int) size);
    }
  }

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
    size_t init_sz = get_committed(base, 2 * size);

    // Reserve and commit the top half.
    VirtualMemoryTracker::add_reserved_region(base, size, empty_stack, mtThreadStack);
    VirtualMemoryTracker::add_committed_region(base + size / 2, size / 2, empty_stack);
    size_t tmp1_sz = get_committed(base, 2 * size);

    // Now pretend we have forgotten to call remove_released_region and allocate an new
    // overlapping region with some commited memory.
    VirtualMemoryTracker::add_reserved_region(base + size / 2, size, empty_stack, mtThreadStack);
    VirtualMemoryTracker::add_committed_region(base + size, size / 2, empty_stack);
    size_t tmp2_sz = get_committed(base, 2 * size);

    // And remove the committed memory again by reserving a partially overlappinbg region.
    // This should mean the committed memory is now the same as the initial committed memory,
    // since the new region has no committed memory.
    VirtualMemoryTracker::add_reserved_region(base, size, empty_stack, mtThreadStack);
    size_t new_sz = get_committed(base, 2 * size);

    // Give back the memory.
    VirtualMemoryTracker::remove_released_region(base, size);
    size_t tmp3_sz = get_committed(base, 2 * size);
    VirtualMemoryTracker::add_reserved_region(base, 2 * size, empty_stack, mtThreadStack);
    size_t tmp4_sz = get_committed(base, 2 * size);
    os::release_memory((char*) base, 2 * size);
    size_t tmp5_sz = get_committed(0, 0);

    // If a parallel thread committed memory concurrently, we get a wrong test result.
    // This should not happen often, so try a few times.
    if (new_sz == init_sz) {
      break;
    }

    // If it fails too often log the values we see.
    if (i < 50) {
      tty->print_cr("init_sz: %d, tmp1_sz %d, tmp2_sz %d, tmp3_sz %d, tmp4_sz %d, tmp5_sz %d, new_sz %d, diff %d, region_size %d", (int) init_sz,
                    (int) tmp1_sz, (int) tmp2_sz, (int) tmp3_sz, (int) tmp4_sz, (int) tmp5_sz, (int) new_sz, (int) (new_sz - init_sz), (int) size);
    }

    // Trigger a test failure on the last run.
    if (i == 0) {
      EXPECT_TRUE(new_sz == init_sz) << "new_sz: " << new_sz << ", init_sz: " << init_sz <<
                                        ", diff: " << (new_sz - init_sz) << ", region size: " << size;
    }
  }
}
