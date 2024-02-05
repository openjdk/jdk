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
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"
#include "testutils.hpp"

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

///////

static void test_overwrite_front() {
  address p = (address) os::malloc(1, mtTest);
  *(p - 1) = 'a';
  os::free(p);
}

DEFINE_TEST(test_overwrite_front, "header canary broken")

///////

static void test_overwrite_back() {
  address p = (address) os::malloc(1, mtTest);
  *(p + 1) = 'a';
  os::free(p);
}

DEFINE_TEST(test_overwrite_back, "footer canary broken")

///////

// A overwrite farther away from the NMT header; the report should show the hex dump split up
// in two parts, containing both header and corruption site.
static void test_overwrite_back_long(size_t distance) {
  address p = (address) os::malloc(distance, mtTest);
  *(p + distance) = 'a';
  os::free(p);
}
static void test_overwrite_back_long_aligned_distance()   { test_overwrite_back_long(0x2000); }
DEFINE_TEST(test_overwrite_back_long_aligned_distance, "footer canary broken")
static void test_overwrite_back_long_unaligned_distance() { test_overwrite_back_long(0x2001); }
DEFINE_TEST(test_overwrite_back_long_unaligned_distance, "footer canary broken")

///////

static void test_double_free() {
  address p = (address) os::malloc(1, mtTest);
  os::free(p);
  // Now a double free. Note that this is susceptible to concurrency issues should
  // a concurrent thread have done a malloc and gotten the same address after the
  // first free. To decrease chance of this happening, we repeat the double free
  // several times.
  for (int i = 0; i < 100; i ++) {
    os::free(p);
  }
}

// What assertion message we will see depends on whether the VM wipes the memory-to-be-freed
// on the first free(), and whether the libc uses the freed memory to store bookkeeping information.
// If the death marker in the header is still intact after the first free, we will recognize this as
// double free; if it got wiped, we should at least see a broken header canary.
// The message would be either
// - "header canary broken" or
// - "header canary dead (double free?)".
// However, since gtest regex expressions do not support unions (a|b), I search for a reasonable
// subset here.
DEFINE_TEST(test_double_free, "header canary")

///////

static void test_invalid_block_address() {
  // very low, like the result of an overflow or of accessing a null this pointer
  os::free((void*)0x100);
}
DEFINE_TEST(test_invalid_block_address, "invalid block address")

///////

static void test_unaliged_block_address() {
  address p = (address) os::malloc(1, mtTest);
  os::free(p + 6);
}
DEFINE_TEST(test_unaliged_block_address, "block address is unaligned");

///////

// Test that we notice block corruption on realloc too
static void test_corruption_on_realloc(size_t s1, size_t s2) {
  address p1 = (address) os::malloc(s1, mtTest);
  *(p1 + s1) = 'a';
  address p2 = (address) os::realloc(p1, s2, mtTest);

  // Still here?
  tty->print_cr("NMT did not detect corruption on os::realloc?");
  // Note: don't use ASSERT here, that does not work as expected in death tests. Just
  // let the test run its course, it should notice something is amiss.
}
static void test_corruption_on_realloc_growing()    { test_corruption_on_realloc(0x10, 0x11); }
DEFINE_TEST(test_corruption_on_realloc_growing, COMMON_NMT_HEAP_CORRUPTION_MESSAGE_PREFIX);
static void test_corruption_on_realloc_shrinking()  { test_corruption_on_realloc(0x11, 0x10); }
DEFINE_TEST(test_corruption_on_realloc_shrinking, COMMON_NMT_HEAP_CORRUPTION_MESSAGE_PREFIX);

///////

// realloc is the trickiest of the bunch. Test that realloc works and correctly takes over
// NMT header and footer to the resized block. We just test that nothing crashes - if the
// header/footer get corrupted, NMT heap corruption checker will trigger alert on os::free()).
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
