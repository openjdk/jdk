/*
 * Copyright (c) 2021 SAP SE. All rights reserved.
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"

#if INCLUDE_NMT

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

// this should generate two hex dumps, one with the front header, one with the overwritten
// portion.
static void test_overwrite_back_long() {
  address p = (address) os::malloc(0x2000, mtTest);
  *(p + 0x2000) = 'a';
  os::free(p);
}

DEFINE_TEST(test_overwrite_back_long, "footer canary broken")

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
  // very low, like the result of an overflow or of accessing a NULL this pointer
  os::free((void*)0x100);
}
DEFINE_TEST(test_invalid_block_address, "invalid block address")

///////

static void test_unaliged_block_address() {
  address p = (address) os::malloc(1, mtTest);
  os::free(p + 6);
}
DEFINE_TEST(test_unaliged_block_address, "block address is unaligned");

#endif // INCLUDE_NMT
