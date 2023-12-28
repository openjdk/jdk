/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "nmt/mallocTracker.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

// convenience log. switch on if debugging tests. Don't use tty, plain stdio only.
//#define LOG(...) { printf(__VA_ARGS__); printf("\n"); fflush(stdout); }
#define LOG(...)

static size_t get_total_malloc_invocs() {
  return MallocMemorySummary::as_snapshot()->total_count();
}

static size_t get_total_malloc_size() {
  return MallocMemorySummary::as_snapshot()->total();
}

static size_t get_malloc_overhead() {
  return MallocMemorySummary::as_snapshot()->malloc_overhead();
}

struct totals_t { size_t n; size_t s; size_t ovrh; };

static totals_t get_totals() {
  totals_t tot;
  tot.n = get_total_malloc_invocs();
  tot.s = get_total_malloc_size();
  tot.ovrh = get_malloc_overhead();
  return tot;
}

// Concurrent code can malloc and free too, therefore we need to compare with a leeway factor
#define compare_totals(t_real, t_expected) {                                  \
  double leeway_factor = 0.33;                                                \
  size_t leeway_n = (size_t)(((double)t_expected.n) * leeway_factor);         \
  size_t leeway_s = (size_t)(((double)t_expected.s) * leeway_factor);         \
  EXPECT_GE(t_real.n, t_expected.n - leeway_n);                               \
  EXPECT_LE(t_real.n, t_expected.n + leeway_n);                               \
  EXPECT_GE(t_real.s, t_expected.s - leeway_s);                               \
  EXPECT_LE(t_real.s, t_expected.s + leeway_s);                               \
  EXPECT_GE(t_real.ovrh, t_expected.ovrh - (leeway_n * sizeof(MallocHeader)));   \
  EXPECT_LE(t_real.ovrh, t_expected.ovrh + (leeway_n * sizeof(MallocHeader)));   \
  LOG("Deviation: n=" SSIZE_FORMAT ", s=" SSIZE_FORMAT ", ovrh=" SSIZE_FORMAT,   \
      (ssize_t)t_real.n - (ssize_t)t_expected.n,                                 \
      (ssize_t)t_real.s - (ssize_t)t_expected.s,                                 \
      (ssize_t)t_real.ovrh - (ssize_t)t_expected.ovrh);                          \
}

TEST_VM(NMTNumbers, totals) {

  if (!MemTracker::enabled()) {
    // Skip test if NMT is disabled
    return;
  }

  const totals_t t1 = get_totals();

  LOG("t1: " SIZE_FORMAT " - " SIZE_FORMAT " - " SIZE_FORMAT, t1.n, t1.s, t1.ovrh);

  static const int NUM_ALLOCS = 1024 * 16;
  static const int ALLOC_SIZE = 1024;

  void* p[NUM_ALLOCS];
  for (int i = 0; i < NUM_ALLOCS; i ++) {
    // spread over categories
    int category = i % (mt_number_of_types - 1);
    p[i] = NEW_C_HEAP_ARRAY(char, ALLOC_SIZE, (MEMFLAGS)category);
  }

  const totals_t t2 = get_totals();
  LOG("t2: " SIZE_FORMAT " - " SIZE_FORMAT " - " SIZE_FORMAT, t2.n, t2.s, t2.ovrh);

  totals_t t2_expected;
  t2_expected.n = t1.n + NUM_ALLOCS;
  t2_expected.s = t1.s + ALLOC_SIZE * NUM_ALLOCS;
  t2_expected.ovrh = (t1.n + NUM_ALLOCS) * sizeof(MallocHeader);

  LOG("t2 expected: " SIZE_FORMAT " - " SIZE_FORMAT " - " SIZE_FORMAT, t2_expected.n, t2_expected.s, t2_expected.ovrh);

  compare_totals(t2, t2_expected);

  for (int i = 0; i < NUM_ALLOCS; i ++) {
    os::free(p[i]);
  }

  const totals_t t3 = get_totals();
  LOG("t3: " SIZE_FORMAT " - " SIZE_FORMAT " - " SIZE_FORMAT, t3.n, t3.s, t3.ovrh);

  compare_totals(t3, t1);

}
