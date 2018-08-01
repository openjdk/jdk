/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"

static size_t small_page_size() {
  return os::vm_page_size();
}

static size_t large_page_size() {
  const size_t large_page_size_example = 4 * M;
  return os::page_size_for_region_aligned(large_page_size_example, 1);
}

TEST_VM(os, page_size_for_region) {
  size_t large_page_example = 4 * M;
  size_t large_page = os::page_size_for_region_aligned(large_page_example, 1);

  size_t small_page = os::vm_page_size();
  if (large_page > small_page) {
    size_t num_small_in_large = large_page / small_page;
    size_t page = os::page_size_for_region_aligned(large_page, num_small_in_large);
    ASSERT_EQ(page, small_page) << "Did not get a small page";
  }
}

TEST_VM(os, page_size_for_region_aligned) {
  if (UseLargePages) {
    const size_t small_page = small_page_size();
    const size_t large_page = large_page_size();

    if (large_page > small_page) {
      size_t num_small_pages_in_large = large_page / small_page;
      size_t page = os::page_size_for_region_aligned(large_page, num_small_pages_in_large);

      ASSERT_EQ(page, small_page);
    }
  }
}

TEST_VM(os, page_size_for_region_alignment) {
  if (UseLargePages) {
    const size_t small_page = small_page_size();
    const size_t large_page = large_page_size();
    if (large_page > small_page) {
      const size_t unaligned_region = large_page + 17;
      size_t page = os::page_size_for_region_aligned(unaligned_region, 1);
      ASSERT_EQ(page, small_page);

      const size_t num_pages = 5;
      const size_t aligned_region = large_page * num_pages;
      page = os::page_size_for_region_aligned(aligned_region, num_pages);
      ASSERT_EQ(page, large_page);
    }
  }
}

TEST_VM(os, page_size_for_region_unaligned) {
  if (UseLargePages) {
    // Given exact page size, should return that page size.
    for (size_t i = 0; os::_page_sizes[i] != 0; i++) {
      size_t expected = os::_page_sizes[i];
      size_t actual = os::page_size_for_region_unaligned(expected, 1);
      ASSERT_EQ(expected, actual);
    }

    // Given slightly larger size than a page size, return the page size.
    for (size_t i = 0; os::_page_sizes[i] != 0; i++) {
      size_t expected = os::_page_sizes[i];
      size_t actual = os::page_size_for_region_unaligned(expected + 17, 1);
      ASSERT_EQ(expected, actual);
    }

    // Given a slightly smaller size than a page size,
    // return the next smaller page size.
    if (os::_page_sizes[1] > os::_page_sizes[0]) {
      size_t expected = os::_page_sizes[0];
      size_t actual = os::page_size_for_region_unaligned(os::_page_sizes[1] - 17, 1);
      ASSERT_EQ(actual, expected);
    }

    // Return small page size for values less than a small page.
    size_t small_page = small_page_size();
    size_t actual = os::page_size_for_region_unaligned(small_page - 17, 1);
    ASSERT_EQ(small_page, actual);
  }
}

TEST(os, test_random) {
  const double m = 2147483647;
  double mean = 0.0, variance = 0.0, t;
  const int reps = 10000;
  unsigned int seed = 1;

  // tty->print_cr("seed %ld for %ld repeats...", seed, reps);
  os::init_random(seed);
  int num;
  for (int k = 0; k < reps; k++) {
    num = os::random();
    double u = (double)num / m;
    ASSERT_TRUE(u >= 0.0 && u <= 1.0) << "bad random number!";

    // calculate mean and variance of the random sequence
    mean += u;
    variance += (u*u);
  }
  mean /= reps;
  variance /= (reps - 1);

  ASSERT_EQ(num, 1043618065) << "bad seed";
  // tty->print_cr("mean of the 1st 10000 numbers: %f", mean);
  int intmean = mean*100;
  ASSERT_EQ(intmean, 50);
  // tty->print_cr("variance of the 1st 10000 numbers: %f", variance);
  int intvariance = variance*100;
  ASSERT_EQ(intvariance, 33);
  const double eps = 0.0001;
  t = fabsd(mean - 0.5018);
  ASSERT_LT(t, eps) << "bad mean";
  t = (variance - 0.3355) < 0.0 ? -(variance - 0.3355) : variance - 0.3355;
  ASSERT_LT(t, eps) << "bad variance";
}


#ifdef ASSERT
TEST_VM_ASSERT_MSG(os, page_size_for_region_with_zero_min_pages, "sanity") {
  size_t region_size = 16 * os::vm_page_size();
  os::page_size_for_region_aligned(region_size, 0); // should assert
}
#endif

//////////////////////////////////////////////////////////////////////////////
// Test os::vsnprintf and friends.

static void check_snprintf_result(int expected, size_t limit, int actual, bool expect_count) {
  if (expect_count || ((size_t)expected < limit)) {
    ASSERT_EQ(expected, actual);
  } else {
    ASSERT_GT(0, actual);
  }
}

// PrintFn is expected to be int (*)(char*, size_t, const char*, ...).
// But jio_snprintf is a C-linkage function with that signature, which
// has a different type on some platforms (like Solaris).
template<typename PrintFn>
static void test_snprintf(PrintFn pf, bool expect_count) {
  const char expected[] = "abcdefghijklmnopqrstuvwxyz";
  const int expected_len = sizeof(expected) - 1;
  const size_t padding_size = 10;
  char buffer[2 * (sizeof(expected) + padding_size)];
  char check_buffer[sizeof(buffer)];
  const char check_char = '1';  // Something not in expected.
  memset(check_buffer, check_char, sizeof(check_buffer));
  const size_t sizes_to_test[] = {
    sizeof(buffer) - padding_size,       // Fits, with plenty of space to spare.
    sizeof(buffer)/2,                    // Fits, with space to spare.
    sizeof(buffer)/4,                    // Doesn't fit.
    sizeof(expected) + padding_size + 1, // Fits, with a little room to spare
    sizeof(expected) + padding_size,     // Fits exactly.
    sizeof(expected) + padding_size - 1, // Doesn't quite fit.
    2,                                   // One char + terminating NUL.
    1,                                   // Only space for terminating NUL.
    0 };                                 // No space at all.
  for (unsigned i = 0; i < ARRAY_SIZE(sizes_to_test); ++i) {
    memset(buffer, check_char, sizeof(buffer)); // To catch stray writes.
    size_t test_size = sizes_to_test[i];
    ResourceMark rm;
    stringStream s;
    s.print("test_size: " SIZE_FORMAT, test_size);
    SCOPED_TRACE(s.as_string());
    size_t prefix_size = padding_size;
    guarantee(test_size <= (sizeof(buffer) - prefix_size), "invariant");
    size_t write_size = MIN2(sizeof(expected), test_size);
    size_t suffix_size = sizeof(buffer) - prefix_size - write_size;
    char* write_start = buffer + prefix_size;
    char* write_end = write_start + write_size;

    int result = pf(write_start, test_size, "%s", expected);

    check_snprintf_result(expected_len, test_size, result, expect_count);

    // Verify expected output.
    if (test_size > 0) {
      ASSERT_EQ(0, strncmp(write_start, expected, write_size - 1));
      // Verify terminating NUL of output.
      ASSERT_EQ('\0', write_start[write_size - 1]);
    } else {
      guarantee(test_size == 0, "invariant");
      guarantee(write_size == 0, "invariant");
      guarantee(prefix_size + suffix_size == sizeof(buffer), "invariant");
      guarantee(write_start == write_end, "invariant");
    }

    // Verify no scribbling on prefix or suffix.
    ASSERT_EQ(0, strncmp(buffer, check_buffer, prefix_size));
    ASSERT_EQ(0, strncmp(write_end, check_buffer, suffix_size));
  }

  // Special case of 0-length buffer with empty (except for terminator) output.
  check_snprintf_result(0, 0, pf(NULL, 0, "%s", ""), expect_count);
  check_snprintf_result(0, 0, pf(NULL, 0, ""), expect_count);
}

// This is probably equivalent to os::snprintf, but we're being
// explicit about what we're testing here.
static int vsnprintf_wrapper(char* buf, size_t len, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  int result = os::vsnprintf(buf, len, fmt, args);
  va_end(args);
  return result;
}

TEST_VM(os, vsnprintf) {
  test_snprintf(vsnprintf_wrapper, true);
}

TEST_VM(os, snprintf) {
  test_snprintf(os::snprintf, true);
}

// These are declared in jvm.h; test here, with related functions.
extern "C" {
int jio_vsnprintf(char*, size_t, const char*, va_list);
int jio_snprintf(char*, size_t, const char*, ...);
}

// This is probably equivalent to jio_snprintf, but we're being
// explicit about what we're testing here.
static int jio_vsnprintf_wrapper(char* buf, size_t len, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  int result = jio_vsnprintf(buf, len, fmt, args);
  va_end(args);
  return result;
}

TEST_VM(os, jio_vsnprintf) {
  test_snprintf(jio_vsnprintf_wrapper, false);
}

TEST_VM(os, jio_snprintf) {
  test_snprintf(jio_snprintf, false);
}
