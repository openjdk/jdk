/*
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "nmt/mallocHeader.inline.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"
#include "sanitizers/address.hpp"
#include "unittest.hpp"

// Uncomment to get test output
//#define LOG_PLEASE
#include "testutils.hpp"

#if !INCLUDE_ASAN

using ::testing::HasSubstr;

static void test_pointer(const void* p, bool expected_return_code, const char* expected_message) {
  stringStream ss;
  const bool b = MemTracker::print_containing_region(p, &ss);
  LOG_HERE("MemTracker::print_containing_region(" PTR_FORMAT ") yielded: %d \"%s\"", p2i(p), b, ss.base());
  EXPECT_EQ(b, expected_return_code);
  if (b) {
    EXPECT_THAT(ss.base(), HasSubstr(expected_message));
  }
}

static void test_for_mmap(size_t sz, ssize_t offset) {
  char* addr = os::reserve_memory(sz, false, mtTest);
  if (MemTracker::enabled()) {
    test_pointer(addr + offset, true, "in mmap'd memory region");
  } else {
    // NMT disabled: we should see nothing.
    test_pointer(addr + offset, false, "");
  }
  os::release_memory(addr, os::vm_page_size());
}

TEST_VM(NMT, location_printing_mmap_1) { test_for_mmap(os::vm_page_size(), 0);  }
TEST_VM(NMT, location_printing_mmap_2) { test_for_mmap(os::vm_page_size(), os::vm_page_size() - 1);  }

#endif // !INCLUDE_ASAN
