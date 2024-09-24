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
#include "nmt/memTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

// Tests the assignment operator of ReservedMemoryRegion
TEST_VM(NMT, ReservedRegionCopy) {
  address dummy1 = (address)0x10000000;
  NativeCallStack stack1(&dummy1, 1);
  ReservedMemoryRegion region1(dummy1, os::vm_page_size(), stack1, mtThreadStack);
  VirtualMemorySummary::record_reserved_memory(os::vm_page_size(), region1.flag());
  region1.add_committed_region(dummy1, os::vm_page_size(), stack1);
  address dummy2 = (address)0x20000000;
  NativeCallStack stack2(&dummy2, 1);
  ReservedMemoryRegion region2(dummy2, os::vm_page_size(), stack2, mtCode);
  VirtualMemorySummary::record_reserved_memory(os::vm_page_size(), region2.flag());
  region2.add_committed_region(dummy2, os::vm_page_size(), stack2);

  region2 = region1;

  CommittedRegionIterator itr = region2.iterate_committed_regions();
  const CommittedMemoryRegion* rgn = itr.next();
  ASSERT_EQ(rgn->base(), dummy1); // Now we should see dummy1
  ASSERT_EQ(region2.flag(), mtThreadStack); // Should be correct flag
  ASSERT_EQ(region2.call_stack()->get_frame(0), dummy1); // Check the stack
  rgn = itr.next();
  ASSERT_EQ(rgn, (const CommittedMemoryRegion*)nullptr); // and nothing else
}

