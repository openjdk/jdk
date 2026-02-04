/*
 * Copyright (c) 2022, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "nmt/mallocTracker.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"
#include "sanitizers/address.hpp"
#include "testutils.hpp"
#include "unittest.hpp"

// Check NMT header for integrity, as well as expected type and size.
static void check_expected_malloc_header(const void* payload, MemTag mem_tag, size_t size) {
  const MallocHeader* hdr = MallocHeader::resolve_checked(payload);
  EXPECT_EQ(hdr->size(), size);
  EXPECT_EQ(hdr->mem_tag(), mem_tag);
}

// ASAN complains about allocating very large sizes
#if !INCLUDE_ASAN

// Check that a malloc with an overflowing size is rejected.
TEST_VM(NMT, malloc_failure1) {
  void* p = os::malloc(SIZE_MAX, mtTest);
  EXPECT_NULL(p);
}

// Check that gigantic mallocs are rejected, even if no size overflow happens.
TEST_VM(NMT, malloc_failure2) {
  void* p = os::malloc(SIZE_MAX - M, mtTest);
  EXPECT_NULL(p);
}

// Check correct handling of failing reallocs.
static void check_failing_realloc(size_t failing_request_size) {

  // We test this with both NMT enabled and disabled.
  bool nmt_enabled = MemTracker::enabled();
  const size_t first_size = 0x100;

  void* p = os::malloc(first_size, mtTest);
  EXPECT_NOT_NULL(p);
  if (nmt_enabled) {
    check_expected_malloc_header(p, mtTest, first_size);
  }
  GtestUtils::mark_range(p, first_size);

  // should fail
  void* p2 = os::realloc(p, failing_request_size, mtTest);
  EXPECT_NULL(p2);

  // original allocation should still be intact
  EXPECT_RANGE_IS_MARKED(p, first_size);
  if (nmt_enabled) {
    check_expected_malloc_header(p, mtTest, first_size);
  }

  os::free(p);
}

TEST_VM(NMT, realloc_failure_overflowing_size) {
  check_failing_realloc(SIZE_MAX);
  check_failing_realloc(SIZE_MAX - MemTracker::overhead_per_malloc());
}

TEST_VM(NMT, realloc_failure_gigantic_size) {
  check_failing_realloc(SIZE_MAX - M);
}

static void* do_realloc(void* p, size_t old_size, size_t new_size, uint8_t old_content, bool check_nmt_header) {

  EXPECT_NOT_NULL(p);
  if (check_nmt_header) {
    check_expected_malloc_header(p, mtTest, old_size);
  }

  void* p2 = os::realloc(p, new_size, mtTest);

  EXPECT_NOT_NULL(p2);
  if (check_nmt_header) {
    check_expected_malloc_header(p2, mtTest, new_size);
  }

  // Check old content, and possibly zapped area (if block grew)
  if (old_size < new_size) {
    EXPECT_RANGE_IS_MARKED_WITH(p2, old_size, old_content);
#ifdef ASSERT
    if (MemTracker::enabled()) {
      EXPECT_RANGE_IS_MARKED_WITH((char*)p2 + old_size, new_size - old_size, uninitBlockPad);
    }
#endif
  } else {
    EXPECT_RANGE_IS_MARKED_WITH(p2, new_size, old_content);
  }

  return p2;
}

// Check a random sequence of reallocs. For enlarging reallocs, we expect the
// newly allocated memory to be zapped (in debug) while the old section should be
// left intact.
TEST_VM(NMT, random_reallocs) {

  bool nmt_enabled = MemTracker::enabled();
  size_t size = 256;
  uint8_t content = 'A';

  void* p = os::malloc(size, mtTest);
  ASSERT_NOT_NULL(p);
  if (nmt_enabled) {
    check_expected_malloc_header(p, mtTest, size);
  }
  GtestUtils::mark_range_with(p, size, content);

  for (int n = 0; n < 100; n ++) {
    size_t new_size = (size_t)(os::random() % 512) + 1;
    // LOG_HERE("reallocating %zu->%zu", size, new_size);
    p = do_realloc(p, size, new_size, content, nmt_enabled);
    size = new_size;
    content = (n % 26) + 'A';
    GtestUtils::mark_range_with(p, size, content);
  }

  os::free(p);
}

TEST_VM(NMT, HeaderKeepsIntegrityAfterRevival) {
  if (!MemTracker::enabled()) {
    return;
  }
  size_t some_size = 16;
  void* p = os::malloc(some_size, mtTest);
  ASSERT_NOT_NULL(p) << "Failed to malloc()";
  MallocHeader* hdr = MallocHeader::kill_block(p);
  MallocHeader::revive_block(p);
  check_expected_malloc_header(p, mtTest, some_size);
}
#endif // !INCLUDE_ASAN