/*
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

static void test_for_live_c_heap_block(size_t sz, ssize_t offset) {
  char* c = NEW_C_HEAP_ARRAY(char, sz, mtTest);
  LOG_HERE("C-block starts " PTR_FORMAT ", size %zu.", p2i(c), sz);
  memset(c, 0, sz);
  if (MemTracker::enabled()) {
    const char* expected_string = "into live malloced block";
    if (offset < 0) {
      expected_string = "into header of live malloced block";
    } else if ((size_t)offset >= sz) {
      expected_string = "just outside of live malloced block";
    }
    test_pointer(c + offset, true, expected_string);
  } else {
    // NMT disabled: we should see nothing.
    test_pointer(c + offset, false, "");
  }
  FREE_C_HEAP_ARRAY(char, c);
}

#ifdef LINUX
static void test_for_dead_c_heap_block(size_t sz, ssize_t offset) {
  if (!MemTracker::enabled()) {
    return;
  }
  char* c = NEW_C_HEAP_ARRAY(char, sz, mtTest);
  LOG_HERE("C-block starts " PTR_FORMAT ", size %zu.", p2i(c), sz);
  memset(c, 0, sz);
  // We cannot just free the allocation to try dead block printing, since the memory
  // may be immediately reused by concurrent code. Instead, we mark the block as dead
  // manually, and revert that before freeing it.
  MallocHeader* const hdr = MallocHeader::resolve_checked(c);
  hdr->mark_block_as_dead();

  const char* expected_string = "into dead malloced block";
  if (offset < 0) {
    expected_string = "into header of dead malloced block";
  } else if ((size_t)offset >= sz) {
    expected_string = "just outside of dead malloced block";
  }

  test_pointer(c + offset, true, expected_string);

  hdr->revive();
  FREE_C_HEAP_ARRAY(char, c);
}
#endif

TEST_VM(NMT, location_printing_cheap_live_1) { test_for_live_c_heap_block(2 * K, 0); }              // start of payload
TEST_VM(NMT, location_printing_cheap_live_2) { test_for_live_c_heap_block(2 * K, -7); }             // into header
TEST_VM(NMT, location_printing_cheap_live_3) { test_for_live_c_heap_block(2 * K, K + 1); }          // into payload
TEST_VM(NMT, location_printing_cheap_live_4) { test_for_live_c_heap_block(2 * K, K + 2); }          // into payload (check for even/odd errors)
TEST_VM(NMT, location_printing_cheap_live_5) { test_for_live_c_heap_block(2 * K + 1, 2 * K + 2); }  // just outside payload
TEST_VM(NMT, location_printing_cheap_live_6) { test_for_live_c_heap_block(4, 0); }                  // into a very small block
TEST_VM(NMT, location_printing_cheap_live_7) { test_for_live_c_heap_block(4, 4); }                  // just outside a very small block

#ifdef LINUX
TEST_VM(NMT, DISABLED_location_printing_cheap_dead_1) { test_for_dead_c_heap_block(2 * K, 0); }              // start of payload
TEST_VM(NMT, DISABLED_location_printing_cheap_dead_2) { test_for_dead_c_heap_block(2 * K, -7); }             // into header
TEST_VM(NMT, DISABLED_location_printing_cheap_dead_3) { test_for_dead_c_heap_block(2 * K, K + 1); }          // into payload
TEST_VM(NMT, DISABLED_location_printing_cheap_dead_4) { test_for_dead_c_heap_block(2 * K, K + 2); }          // into payload (check for even/odd errors)
TEST_VM(NMT, DISABLED_location_printing_cheap_dead_5) { test_for_dead_c_heap_block(2 * K + 1, 2 * K + 2); }  // just outside payload
TEST_VM(NMT, DISABLED_location_printing_cheap_dead_6) { test_for_dead_c_heap_block(4, 0); }                  // into a very small block
TEST_VM(NMT, DISABLED_location_printing_cheap_dead_7) { test_for_dead_c_heap_block(4, 4); }                  // just outside a very small block
#endif

static void test_for_mmap(size_t sz, ssize_t offset) {
  char* addr = os::reserve_memory(sz, mtTest);
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
