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
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/align.hpp"
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

static void do_test_print_hex_dump(address addr, size_t len, int unitsize, const char* expected) {
  char buf[256];
  buf[0] = '\0';
  stringStream ss(buf, sizeof(buf));
  os::print_hex_dump(&ss, addr, addr + len, unitsize);
//  tty->print_cr("expected: %s", expected);
//  tty->print_cr("result: %s", buf);
  ASSERT_NE(strstr(buf, expected), (char*)NULL);
}

TEST_VM(os, test_print_hex_dump) {
  const char* pattern [4] = {
#ifdef VM_LITTLE_ENDIAN
    "00 01 02 03 04 05 06 07",
    "0100 0302 0504 0706",
    "03020100 07060504",
    "0706050403020100"
#else
    "00 01 02 03 04 05 06 07",
    "0001 0203 0405 0607",
    "00010203 04050607",
    "0001020304050607"
#endif
  };

  const char* pattern_not_readable [4] = {
    "?? ?? ?? ?? ?? ?? ?? ??",
    "???? ???? ???? ????",
    "???????? ????????",
    "????????????????"
  };

  // On AIX, zero page is readable.
  address unreadable =
#ifdef AIX
    (address) 0xFFFFFFFFFFFF0000ULL;
#else
    (address) 0
#endif
    ;

  ResourceMark rm;
  char buf[64];
  stringStream ss(buf, sizeof(buf));
  outputStream* out = &ss;
//  outputStream* out = tty; // enable for printout

  // Test dumping unreadable memory
  // Exclude test for Windows for now, since it needs SEH handling to work which cannot be
  // guaranteed when we call directly into VM code. (see JDK-8220220)
#ifndef _WIN32
  do_test_print_hex_dump(unreadable, 100, 1, pattern_not_readable[0]);
  do_test_print_hex_dump(unreadable, 100, 2, pattern_not_readable[1]);
  do_test_print_hex_dump(unreadable, 100, 4, pattern_not_readable[2]);
  do_test_print_hex_dump(unreadable, 100, 8, pattern_not_readable[3]);
#endif

  // Test dumping readable memory
  address arr = (address)os::malloc(100, mtInternal);
  for (int c = 0; c < 100; c++) {
    arr[c] = c;
  }

  // properly aligned
  do_test_print_hex_dump(arr, 100, 1, pattern[0]);
  do_test_print_hex_dump(arr, 100, 2, pattern[1]);
  do_test_print_hex_dump(arr, 100, 4, pattern[2]);
  do_test_print_hex_dump(arr, 100, 8, pattern[3]);

  // Not properly aligned. Should automatically down-align by unitsize
  do_test_print_hex_dump(arr + 1, 100, 2, pattern[1]);
  do_test_print_hex_dump(arr + 1, 100, 4, pattern[2]);
  do_test_print_hex_dump(arr + 1, 100, 8, pattern[3]);

  os::free(arr);
}

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

// Test that os::release_memory() can deal with areas containing multiple mappings.
#define PRINT_MAPPINGS(s) { tty->print_cr("%s", s); os::print_memory_mappings((char*)p, total_range_len, tty); }
//#define PRINT_MAPPINGS

#ifndef _AIX // JDK-8257041
// Reserve an area consisting of multiple mappings
//  (from multiple calls to os::reserve_memory)
static address reserve_multiple(int num_stripes, size_t stripe_len) {
  assert(is_aligned(stripe_len, os::vm_allocation_granularity()), "Sanity");
  size_t total_range_len = num_stripes * stripe_len;
  // Reserve a large contiguous area to get the address space...
  address p = (address)os::reserve_memory(total_range_len);
  EXPECT_NE(p, (address)NULL);
  // .. release it...
  EXPECT_TRUE(os::release_memory((char*)p, total_range_len));
  // ... re-reserve in the same spot multiple areas...
  for (int stripe = 0; stripe < num_stripes; stripe ++) {
    address q = p + (stripe * stripe_len);
    q = (address)os::attempt_reserve_memory_at((char*)q, stripe_len);
    EXPECT_NE(q, (address)NULL);
    // Commit, alternatingly with or without exec permission,
    //  to prevent kernel from folding these mappings.
    const bool executable = stripe % 2 == 0;
    EXPECT_TRUE(os::commit_memory((char*)q, stripe_len, executable));
  }
  return p;
}
#endif // !AIX

// Reserve an area with a single call to os::reserve_memory,
//  with multiple committed and uncommitted regions
static address reserve_one_commit_multiple(int num_stripes, size_t stripe_len) {
  assert(is_aligned(stripe_len, os::vm_allocation_granularity()), "Sanity");
  size_t total_range_len = num_stripes * stripe_len;
  address p = (address)os::reserve_memory(total_range_len);
  EXPECT_NE(p, (address)NULL);
  for (int stripe = 0; stripe < num_stripes; stripe ++) {
    address q = p + (stripe * stripe_len);
    if (stripe % 2 == 0) {
      EXPECT_TRUE(os::commit_memory((char*)q, stripe_len, false));
    }
  }
  return p;
}

#ifdef _WIN32
// Release a range allocated with reserve_multiple carefully, to not trip mapping
// asserts on Windows in os::release_memory()
static void carefully_release_multiple(address start, int num_stripes, size_t stripe_len) {
  for (int stripe = 0; stripe < num_stripes; stripe ++) {
    address q = start + (stripe * stripe_len);
    EXPECT_TRUE(os::release_memory((char*)q, stripe_len));
  }
}
struct NUMASwitcher {
  const bool _b;
  NUMASwitcher(bool v): _b(UseNUMAInterleaving) { UseNUMAInterleaving = v; }
  ~NUMASwitcher() { UseNUMAInterleaving = _b; }
};
#endif

#ifndef _AIX // JDK-8257041
TEST_VM(os, release_multi_mappings) {
  // Test that we can release an area created with multiple reservation calls
  const size_t stripe_len = 4 * M;
  const int num_stripes = 4;
  const size_t total_range_len = stripe_len * num_stripes;

  // reserve address space...
  address p = reserve_multiple(num_stripes, stripe_len);
  ASSERT_NE(p, (address)NULL);
  PRINT_MAPPINGS("A");

  // .. release it...
  {
    // On Windows, use UseNUMAInterleaving=1 which makes
    //  os::release_memory accept multi-map-ranges.
    //  Otherwise we would assert (see below for death test).
    WINDOWS_ONLY(NUMASwitcher b(true);)
    ASSERT_TRUE(os::release_memory((char*)p, total_range_len));
  }
  PRINT_MAPPINGS("B");

  // re-reserve it. This should work unless release failed.
  address p2 = (address)os::attempt_reserve_memory_at((char*)p, total_range_len);
  ASSERT_EQ(p2, p);
  PRINT_MAPPINGS("C");

  ASSERT_TRUE(os::release_memory((char*)p, total_range_len));
}
#endif // !AIX

#ifdef _WIN32
// On Windows, test that we recognize bad ranges.
//  On debug this would assert. Test that too.
//  On other platforms, we are unable to recognize bad ranges.
#ifdef ASSERT
TEST_VM_ASSERT_MSG(os, release_bad_ranges, "bad release") {
#else
TEST_VM(os, release_bad_ranges) {
#endif
  char* p = os::reserve_memory(4 * M);
  ASSERT_NE(p, (char*)NULL);
  // Release part of range
  ASSERT_FALSE(os::release_memory(p, M));
  // Release part of range
  ASSERT_FALSE(os::release_memory(p + M, M));
  // Release more than the range (explicitly switch off NUMA here
  //  to make os::release_memory() test more strictly and to not
  //  accidentally release neighbors)
  {
    NUMASwitcher b(false);
    ASSERT_FALSE(os::release_memory(p, M * 5));
    ASSERT_FALSE(os::release_memory(p - M, M * 5));
    ASSERT_FALSE(os::release_memory(p - M, M * 6));
  }

  ASSERT_TRUE(os::release_memory(p, 4 * M)); // Release for real
  ASSERT_FALSE(os::release_memory(p, 4 * M)); // Again, should fail
}
#endif // _WIN32

TEST_VM(os, release_one_mapping_multi_commits) {
  // Test that we can release an area consisting of interleaved
  //  committed and uncommitted regions:
  const size_t stripe_len = 4 * M;
  const int num_stripes = 4;
  const size_t total_range_len = stripe_len * num_stripes;

  // reserve address space...
  address p = reserve_one_commit_multiple(num_stripes, stripe_len);
  ASSERT_NE(p, (address)NULL);
  PRINT_MAPPINGS("A");

  // .. release it...
  ASSERT_TRUE(os::release_memory((char*)p, total_range_len));
  PRINT_MAPPINGS("B");

  // re-reserve it. This should work unless release failed.
  address p2 = (address)os::attempt_reserve_memory_at((char*)p, total_range_len);
  ASSERT_EQ(p2, p);
  PRINT_MAPPINGS("C");

  ASSERT_TRUE(os::release_memory((char*)p, total_range_len));
  PRINT_MAPPINGS("D");
}

TEST_VM(os, show_mappings_1) {
  // Display an arbitrary large address range. Make this works, does not hang, etc.
  char dummy[16 * K]; // silent truncation is fine, we don't care.
  stringStream ss(dummy, sizeof(dummy));
  os::print_memory_mappings((char*)0x1000, LP64_ONLY(1024) NOT_LP64(3) * G, &ss);
}

#ifdef _WIN32
// Test os::win32::find_mapping
TEST_VM(os, find_mapping_simple) {
  const size_t total_range_len = 4 * M;
  os::win32::mapping_info_t mapping_info;

  // Some obvious negatives
  ASSERT_FALSE(os::win32::find_mapping((address)NULL, &mapping_info));
  ASSERT_FALSE(os::win32::find_mapping((address)4711, &mapping_info));

  // A simple allocation
  {
    address p = (address)os::reserve_memory(total_range_len);
    ASSERT_NE(p, (address)NULL);
    PRINT_MAPPINGS("A");
    for (size_t offset = 0; offset < total_range_len; offset += 4711) {
      ASSERT_TRUE(os::win32::find_mapping(p + offset, &mapping_info));
      ASSERT_EQ(mapping_info.base, p);
      ASSERT_EQ(mapping_info.regions, 1);
      ASSERT_EQ(mapping_info.size, total_range_len);
      ASSERT_EQ(mapping_info.committed_size, 0);
    }
    // Test just outside the allocation
    if (os::win32::find_mapping(p - 1, &mapping_info)) {
      ASSERT_NE(mapping_info.base, p);
    }
    if (os::win32::find_mapping(p + total_range_len, &mapping_info)) {
      ASSERT_NE(mapping_info.base, p);
    }
    ASSERT_TRUE(os::release_memory((char*)p, total_range_len));
    PRINT_MAPPINGS("B");
    ASSERT_FALSE(os::win32::find_mapping(p, &mapping_info));
  }
}

TEST_VM(os, find_mapping_2) {
  // A more complex allocation, consisting of multiple regions.
  const size_t total_range_len = 4 * M;
  os::win32::mapping_info_t mapping_info;

  const size_t stripe_len = total_range_len / 4;
  address p = reserve_one_commit_multiple(4, stripe_len);
  ASSERT_NE(p, (address)NULL);
  PRINT_MAPPINGS("A");
  for (size_t offset = 0; offset < total_range_len; offset += 4711) {
    ASSERT_TRUE(os::win32::find_mapping(p + offset, &mapping_info));
    ASSERT_EQ(mapping_info.base, p);
    ASSERT_EQ(mapping_info.regions, 4);
    ASSERT_EQ(mapping_info.size, total_range_len);
    ASSERT_EQ(mapping_info.committed_size, total_range_len / 2);
  }
  // Test just outside the allocation
  if (os::win32::find_mapping(p - 1, &mapping_info)) {
    ASSERT_NE(mapping_info.base, p);
  }
  if (os::win32::find_mapping(p + total_range_len, &mapping_info)) {
    ASSERT_NE(mapping_info.base, p);
  }
  ASSERT_TRUE(os::release_memory((char*)p, total_range_len));
  PRINT_MAPPINGS("B");
  ASSERT_FALSE(os::win32::find_mapping(p, &mapping_info));
}

TEST_VM(os, find_mapping_3) {
  const size_t total_range_len = 4 * M;
  os::win32::mapping_info_t mapping_info;

  // A more complex case, consisting of multiple allocations.
  {
    const size_t stripe_len = total_range_len / 4;
    address p = reserve_multiple(4, stripe_len);
    ASSERT_NE(p, (address)NULL);
    PRINT_MAPPINGS("E");
    for (int stripe = 0; stripe < 4; stripe ++) {
      ASSERT_TRUE(os::win32::find_mapping(p + (stripe * stripe_len), &mapping_info));
      ASSERT_EQ(mapping_info.base, p + (stripe * stripe_len));
      ASSERT_EQ(mapping_info.regions, 1);
      ASSERT_EQ(mapping_info.size, stripe_len);
      ASSERT_EQ(mapping_info.committed_size, stripe_len);
    }
    carefully_release_multiple(p, 4, stripe_len);
    PRINT_MAPPINGS("F");
    ASSERT_FALSE(os::win32::find_mapping(p, &mapping_info));
  }
}
#endif // _WIN32
