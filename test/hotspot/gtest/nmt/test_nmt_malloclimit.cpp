/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/arena.hpp"
#include "nmt/mallocLimit.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/nmtCommon.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "testutils.hpp"
#include "unittest.hpp"

// Tests here just test the MallocLimit option parser. They are complemented
// by more extensive jtreg tests (runtime/NMT/TestMallocLimit.java)
static bool compare_limits(const malloclimit* a, const malloclimit* b) {
  return a->sz == b->sz && a->mode == b->mode;
}

static bool compare_sets(const MallocLimitSet* a, const MallocLimitSet* b) {
  if (compare_limits(a->global_limit(), b->global_limit())) {
    for (int i = 0; i < mt_number_of_tags; i++) {
      if (!compare_limits(a->mem_tag_limit(NMTUtil::index_to_tag(i)),
                          b->mem_tag_limit(NMTUtil::index_to_tag(i)))) {
        return false;
      }
    }
  }
  return true;
}

static void test(const char* s, const MallocLimitSet& expected) {
  MallocLimitSet set;
  const char* err;
  EXPECT_TRUE(set.parse_malloclimit_option(s, &err)) << err;
  EXPECT_TRUE(compare_sets(&set, &expected));
}

TEST(NMT, MallocLimitBasics) {
  MallocLimitSet expected;

  expected.set_global_limit(1 * G, MallocLimitMode::trigger_fatal);
  test("1g", expected);
  test("1024m", expected);
  test("1048576k", expected);
  test("1073741824", expected);

  // Fatal is default, but can be specified explicitely
  test("1g:fatal", expected);

  expected.set_global_limit(2 * M, MallocLimitMode::trigger_oom);
  test("2m:oom", expected);
  test("2m:OOM", expected);
  test("2048k:oom", expected);
}

TEST(NMT, MallocLimitPerCategory) {
  MallocLimitSet expected;

  expected.set_category_limit(mtMetaspace, 1 * M, MallocLimitMode::trigger_fatal);
  test("metaspace:1m", expected);
  test("metaspace:1m:fatal", expected);
  test("METASPACE:1m", expected);

  expected.set_category_limit(mtCompiler, 2 * M, MallocLimitMode::trigger_oom);
  expected.set_category_limit(mtThread, 3 * M, MallocLimitMode::trigger_oom);
  expected.set_category_limit(mtThreadStack, 4 * M, MallocLimitMode::trigger_oom);
  expected.set_category_limit(mtClass, 5 * M, MallocLimitMode::trigger_fatal);
  expected.set_category_limit(mtClassShared, 6 * M, MallocLimitMode::trigger_fatal);
  test("metaspace:1m,compiler:2m:oom,thread:3m:oom,threadstack:4m:oom,class:5m,classshared:6m", expected);
}

TEST(NMT, MallocLimitMemTagEnumNames) {
  MallocLimitSet expected;
  stringStream option;
  for (int i = 0; i < mt_number_of_tags; i++) {
    MemTag mem_tag = NMTUtil::index_to_tag(i);
    if (mem_tag != MemTag::mtNone) {
      expected.set_category_limit(mem_tag, (i + 1) * M, MallocLimitMode::trigger_fatal);
      option.print("%s%s:%dM", (i > 0 ? "," : ""), NMTUtil::tag_to_enum_name(mem_tag), i + 1);
    }
  }
  test(option.base(), expected);
}

TEST(NMT, MallocLimitAllCategoriesHaveHumanReadableNames) {
  MallocLimitSet expected;
  stringStream option;
  for (int i = 0; i < mt_number_of_tags; i++) {
    MemTag mem_tag = NMTUtil::index_to_tag(i);
    if (mem_tag != MemTag::mtNone) {
      expected.set_category_limit(mem_tag, (i + 1) * M, MallocLimitMode::trigger_fatal);
      option.print("%s%s:%dM", (i > 0 ? "," : ""), NMTUtil::tag_to_name(mem_tag), i + 1);
    }
  }
  test(option.base(), expected);
}

static void test_failing(const char* s) {
  MallocLimitSet set;
  const char* err;
  ASSERT_FALSE(set.parse_malloclimit_option(s, &err));
}

TEST(NMT, MallocLimitBadOptions) {
  test_failing("abcd");
  test_failing("compiler:1g:");
  test_failing("compiler:1g:oom:mtTest:asas:1m");
}

// Death tests.
// Majority of MallocLimit functional tests are done via jtreg test runtime/NMT/MallocLimitTest. Here, we just
// test that limits are triggered for specific APIs.
TEST_VM_FATAL_ERROR_MSG(NMT, MallocLimitDeathTestOnRealloc, ".*MallocLimit: reached category .mtTest. limit.*") {
  // We fake the correct assert if NMT is off to make the test pass (there is no way to execute a death test conditionally)
  if (!MemTracker::enabled()) {
    fatal("Fake message please ignore: MallocLimit: reached category \"mtTest\" limit");
  }
  // the real test
  MallocLimitHandler::initialize("test:100m:fatal");
  char* p = (char*)os::malloc(2, mtTest);
  p = (char*)os::realloc(p, 120 * M, mtTest);
}

TEST_VM_FATAL_ERROR_MSG(NMT, MallocLimitDeathTestOnStrDup, ".*MallocLimit: reached category .mtTest. limit.*") {
  // We fake the correct assert if NMT is off to make the test pass (there is no way to execute a death test conditionally)
  if (!MemTracker::enabled()) {
    fatal("Fake message please ignore: MallocLimit: reached category \"mtTest\" limit");
  }
  // the real test
  MallocLimitHandler::initialize("test:10m:fatal");
  for (int i = 0; i < 100000; i++) {
    char* p = os::strdup("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", mtTest);
  }
}

TEST_VM_FATAL_ERROR_MSG(NMT, MallocLimitDeathTestOnArenaGrow, ".*MallocLimit in Arena::grow.*") {
  // We fake the correct assert if NMT is off to make the test pass (there is no way to execute a death test conditionally)
  if (!MemTracker::enabled()) {
    fatal("Fake message please ignore: MallocLimit in Arena::grow");
  }
  // the real test
  MallocLimitHandler::initialize("test:10m:oom");
  Arena ar(mtTest);
  ar.Amalloc(10 * M, AllocFailStrategy::EXIT_OOM);
}
