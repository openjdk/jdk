/*
 * Copyright (c) 2022, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "services/mallocHeader.inline.hpp"
#include "services/mallocTracker.hpp"
#include "services/memTracker.hpp"
#include "testutils.hpp"
#include "unittest.hpp"

// Check NMT header for integrity, as well as expected type and size.
static void check_expected_malloc_header(const void* payload, MEMFLAGS type, size_t size) {
  const MallocHeader* hdr = MallocHeader::resolve_checked(payload);
  EXPECT_EQ(hdr->size(), size);
  EXPECT_EQ(hdr->flags(), type);
}

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

TEST_VM(NMT, HeaderKeepsIntegrityAfterRevival) {
  if (!MemTracker::enabled()) {
    return;
  }
  size_t some_size = 16;
  void* p = os::malloc(some_size, mtTest);
  ASSERT_NOT_NULL(p) << "Failed to malloc()";
  MallocHeader* hdr = MallocTracker::malloc_header(p);
  hdr->mark_block_as_dead();
  hdr->revive();
  check_expected_malloc_header(p, mtTest, some_size);
}
