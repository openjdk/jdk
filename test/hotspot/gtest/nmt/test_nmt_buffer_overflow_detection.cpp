/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"
#include "sanitizers/address.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"
#include "testutils.hpp"

#if !INCLUDE_ASAN

// This prefix shows up on any c heap corruption NMT detects. If unsure which assert will
// come, just use this one.
#define COMMON_NMT_HEAP_CORRUPTION_MESSAGE_PREFIX "NMT corruption"

#define DEFINE_TEST(test_function, expected_assertion_message)                            \
  TEST_VM_FATAL_ERROR_MSG(NMT, test_function, ".*" expected_assertion_message ".*") {     \
    if (MemTracker::tracking_level() > NMT_off) {                                         \
      tty->print_cr("NMT overwrite death test, please ignore subsequent error dump.");    \
      test_function ();                                                                   \
    } else {                                                                              \
      /* overflow detection requires NMT to be on. If off, fake assert. */                \
      guarantee(false,                                                                    \
                "fake message ignore this - " expected_assertion_message);                \
    }                                                                                     \
  }

static void test_invalid_block_address() {
  // very low, like the result of an overflow or of accessing a null this pointer
  os::free((void*)0x100);
}
DEFINE_TEST(test_invalid_block_address, "invalid block address")

static void test_unaliged_block_address() {
  address p = (address) os::malloc(1, mtTest);
  os::free(p + 6);
}
DEFINE_TEST(test_unaliged_block_address, "block address is unaligned");

// realloc is the trickiest of the bunch. Test that realloc works and correctly takes over
// NMT header and footer to the resized block. We just test that nothing crashes.
TEST_VM(NMT, test_realloc) {
  // We test both directions (growing and shrinking) and a small range for each to cover all
  // size alignment variants. Should not matter, but this should be cheap.
  for (size_t s1 = 0xF0; s1 < 0x110; s1 ++) {
    for (size_t s2 = 0x100; s2 > 0xF0; s2 --) {
      address p1 = (address) os::malloc(s1, mtTest);
      ASSERT_NOT_NULL(p1);
      GtestUtils::mark_range(p1, s1);       // mark payload range...
      address p2 = (address) os::realloc(p1, s2, mtTest);
      ASSERT_NOT_NULL(p2);
      ASSERT_RANGE_IS_MARKED(p2, MIN2(s1, s2))        // ... and check that it survived the resize
         << s1 << "->" << s2 << std::endl;
      os::free(p2);                         // <- if NMT headers/footers got corrupted this asserts
    }
  }
}

#endif // !INCLUDE_ASAN
