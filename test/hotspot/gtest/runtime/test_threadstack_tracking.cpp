/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

// Included early because the NMT flags don't include it.
#include "utilities/macros.hpp"

#include "runtime/thread.hpp"
#include "services/memTracker.hpp"
#include "services/virtualMemoryTracker.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"


class ThreadStackTrackingTest {
public:
  static void test() {
    VirtualMemoryTracker::initialize(NMT_detail);
    VirtualMemoryTracker::late_initialize(NMT_detail);

    Thread* thr = Thread::current();
    address stack_end = thr->stack_end();
    size_t  stack_size = thr->stack_size();

    MemTracker::record_thread_stack(stack_end, stack_size);

    VirtualMemoryTracker::add_reserved_region(stack_end, stack_size, CALLER_PC, mtThreadStack);

    // snapshot current stack usage
    VirtualMemoryTracker::snapshot_thread_stacks();

    ReservedMemoryRegion* rmr = VirtualMemoryTracker::_reserved_regions->find(ReservedMemoryRegion(stack_end, stack_size));
    ASSERT_TRUE(rmr != NULL);

    ASSERT_EQ(rmr->base(), stack_end);
    ASSERT_EQ(rmr->size(), stack_size);

    CommittedRegionIterator iter = rmr->iterate_committed_regions();
    int i = 0;
    address i_addr = (address)&i;

    // stack grows downward
    address stack_top = stack_end + stack_size;
    bool found_stack_top = false;

    for (const CommittedMemoryRegion* region = iter.next(); region != NULL; region = iter.next()) {
      if (region->base() + region->size() == stack_top) {
        // This should be active part, "i" should be here
        ASSERT_TRUE(i_addr < stack_top && i_addr >= region->base());
        ASSERT_TRUE(region->size() <= stack_size);
        found_stack_top = true;
      }

      i++;
    }

    // NMT was not turned on when the thread was created, so we don't have guard pages
    ASSERT_TRUE(i == 1);
    ASSERT_TRUE(found_stack_top);
  }
};

TEST_VM(VirtualMemoryTracker, thread_stack_tracking) {
  ThreadStackTrackingTest::test();
}
