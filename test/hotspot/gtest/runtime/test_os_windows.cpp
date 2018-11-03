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

#ifdef _WINDOWS

#include "runtime/os.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/globals_extension.hpp"
#include "unittest.hpp"

namespace {
  class MemoryReleaser {
    char* const _ptr;
    const size_t _size;
   public:
    MemoryReleaser(char* ptr, size_t size) : _ptr(ptr), _size(size) { }
    ~MemoryReleaser() {
      os::release_memory_special(_ptr, _size);
    }
  };
}

// test tries to allocate memory in a single contiguous memory block at a particular address.
// The test first tries to find a good approximate address to allocate at by using the same
// method to allocate some memory at any address. The test then tries to allocate memory in
// the vicinity (not directly after it to avoid possible by-chance use of that location)
// This is of course only some dodgy assumption, there is no guarantee that the vicinity of
// the previously allocated memory is available for allocation. The only actual failure
// that is reported is when the test tries to allocate at a particular location but gets a
// different valid one. A NULL return value at this point is not considered an error but may
// be legitimate.
TEST_VM(os_windows, reserve_memory_special) {
  if (!UseLargePages) {
    return;
  }

  // set globals to make sure we hit the correct code path
  FLAG_GUARD(UseLargePagesIndividualAllocation);
  FLAG_GUARD(UseNUMAInterleaving);
  FLAG_SET_CMDLINE(bool, UseLargePagesIndividualAllocation, false);
  FLAG_SET_CMDLINE(bool, UseNUMAInterleaving, false);

  const size_t large_allocation_size = os::large_page_size() * 4;
  char* result = os::reserve_memory_special(large_allocation_size, os::large_page_size(), NULL, false);
  if (result != NULL) {
      // failed to allocate memory, skipping the test
      return;
  }
  MemoryReleaser mr(result, large_allocation_size);

  // allocate another page within the recently allocated memory area which seems to be a good location. At least
  // we managed to get it once.
  const size_t expected_allocation_size = os::large_page_size();
  char* expected_location = result + os::large_page_size();
  char* actual_location = os::reserve_memory_special(expected_allocation_size, os::large_page_size(), expected_location, false);
  if (actual_location != NULL) {
      // failed to allocate memory, skipping the test
      return;
  }
  MemoryReleaser mr2(actual_location, expected_allocation_size);

  EXPECT_EQ(expected_location, actual_location)
        << "Failed to allocate memory at requested location " << expected_location << " of size " << expected_allocation_size;
}

#endif
