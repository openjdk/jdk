/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/thread.hpp"
#include "runtime/threads.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"
#ifdef _WIN32
#include "os_windows.hpp"
#endif

using testing::HasSubstr;

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
    for (size_t s = os::page_sizes().largest(); s != 0; s = os::page_sizes().next_smaller(s)) {
      size_t actual = os::page_size_for_region_unaligned(s, 1);
      ASSERT_EQ(s, actual);
    }

    // Given slightly larger size than a page size, return the page size.
    for (size_t s = os::page_sizes().largest(); s != 0; s = os::page_sizes().next_smaller(s)) {
      size_t actual = os::page_size_for_region_unaligned(s + 17, 1);
      ASSERT_EQ(s, actual);
    }

    // Given a slightly smaller size than a page size,
    // return the next smaller page size.
    for (size_t s = os::page_sizes().largest(); s != 0; s = os::page_sizes().next_smaller(s)) {
      const size_t expected = os::page_sizes().next_smaller(s);
      if (expected != 0) {
        size_t actual = os::page_size_for_region_unaligned(s - 17, 1);
        ASSERT_EQ(actual, expected);
      }
    }

    // Return small page size for values less than a small page.
    size_t small_page = os::page_sizes().smallest();
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
  int num;
  for (int k = 0; k < reps; k++) {
    // Use next_random so the calculation is stateless.
    num = seed = os::next_random(seed);
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
  int intmean = (int)(mean*100);
  ASSERT_EQ(intmean, 50);
  // tty->print_cr("variance of the 1st 10000 numbers: %f", variance);
  int intvariance = (int)(variance*100);
  ASSERT_EQ(intvariance, 33);
  const double eps = 0.0001;
  t = fabsd(mean - 0.5018);
  ASSERT_LT(t, eps) << "bad mean";
  t = (variance - 0.3355) < 0.0 ? -(variance - 0.3355) : variance - 0.3355;
  ASSERT_LT(t, eps) << "bad variance";
}

#ifdef ASSERT
TEST_VM_ASSERT_MSG(os, page_size_for_region_with_zero_min_pages,
                   "assert.min_pages > 0. failed: sanity") {
  size_t region_size = 16 * os::vm_page_size();
  os::page_size_for_region_aligned(region_size, 0); // should assert
}
#endif

#ifndef AIX
// Test relies on the ability to protect memory allocated with os::reserve_memory. AIX may not be able
// to do that (mprotect won't work on System V shm).
static void do_test_print_hex_dump(const_address from, const_address to, int unitsize, int bytes_per_line,
                                   const_address logical_start, const char* expected) {
  char buf[2048];
  buf[0] = '\0';
  stringStream ss(buf, sizeof(buf));
  os::print_hex_dump(&ss, from, to, unitsize, /* print_ascii=*/true, bytes_per_line, logical_start);
  EXPECT_STREQ(buf, expected);
}

// version with a highlighted pc location
static void do_test_print_hex_dump_highlighted(const_address from, const_address to, int unitsize, int bytes_per_line,
                                   const_address logical_start, const char* expected, const_address highlight) {
  char buf[2048];
  buf[0] = '\0';
  stringStream ss(buf, sizeof(buf));
  os::print_hex_dump(&ss, from, to, unitsize, /* print_ascii=*/true, bytes_per_line, logical_start, highlight);
  EXPECT_STREQ(buf, expected);
}

TEST_VM(os, test_print_hex_dump) {

#ifdef _LP64
#define ADDRESS1 "0x0000aaaaaaaaaa00"
#define ADDRESS2 "0x0000aaaaaaaaaa20"
#define ADDRESS3 "0x0000aaaaaaaaaa40"
#else
#define ADDRESS1 "0xaaaaaa00"
#define ADDRESS2 "0xaaaaaa20"
#define ADDRESS3 "0xaaaaaa40"
#endif

#define ASCII_1  "....#.jdk/internal/loader/Native"
#define ASCII_2  "Libraries......."

#define PAT_1 ADDRESS1 ":   ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ??\n" \
              ADDRESS2 ":   ff ff e0 dc 23 00 6a 64 6b 2f 69 6e 74 65 72 6e 61 6c 2f 6c 6f 61 64 65 72 2f 4e 61 74 69 76 65   " ASCII_1 "\n" \
              ADDRESS3 ":   4c 69 62 72 61 72 69 65 73 00 00 00 00 00 00 00                                                   " ASCII_2 "\n"

#define PAT_HL_1A "=>" ADDRESS1 ":   ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ??\n" \
              "  " ADDRESS2 ":   ff ff e0 dc 23 00 6a 64 6b 2f 69 6e 74 65 72 6e 61 6c 2f 6c 6f 61 64 65 72 2f 4e 61 74 69 76 65   " ASCII_1 "\n" \
              "  " ADDRESS3 ":   4c 69 62 72 61 72 69 65 73 00 00 00 00 00 00 00                                                   " ASCII_2 "\n"

#define PAT_HL_1B "  " ADDRESS1 ":   ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ?? ??\n" \
              "=>" ADDRESS2 ":   ff ff e0 dc 23 00 6a 64 6b 2f 69 6e 74 65 72 6e 61 6c 2f 6c 6f 61 64 65 72 2f 4e 61 74 69 76 65   " ASCII_1 "\n" \
              "  " ADDRESS3 ":   4c 69 62 72 61 72 69 65 73 00 00 00 00 00 00 00                                                   " ASCII_2 "\n"

#ifdef VM_LITTLE_ENDIAN
#define PAT_HL_1C "  " ADDRESS1 ":   ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ????\n" \
              "=>" ADDRESS2 ":   ffff dce0 0023 646a 2f6b 6e69 6574 6e72 6c61 6c2f 616f 6564 2f72 614e 6974 6576   " ASCII_1 "\n" \
              "  " ADDRESS3 ":   694c 7262 7261 6569 0073 0000 0000 0000                                           " ASCII_2 "\n"
#else
#define PAT_HL_1C "  " ADDRESS1 ":   ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ????\n" \
              "=>" ADDRESS2 ":   ffff e0dc 2300 6a64 6b2f 696e 7465 726e 616c 2f6c 6f61 6465 722f 4e61 7469 7665   " ASCII_1 "\n" \
              "  " ADDRESS3 ":   4c69 6272 6172 6965 7300 0000 0000 0000                                           " ASCII_2 "\n"
#endif

#ifdef VM_LITTLE_ENDIAN
#define PAT_2 ADDRESS1 ":   ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ????\n" \
              ADDRESS2 ":   ffff dce0 0023 646a 2f6b 6e69 6574 6e72 6c61 6c2f 616f 6564 2f72 614e 6974 6576   " ASCII_1 "\n" \
              ADDRESS3 ":   694c 7262 7261 6569 0073 0000 0000 0000                                           " ASCII_2 "\n"

#define PAT_4 ADDRESS1 ":   ???????? ???????? ???????? ???????? ???????? ???????? ???????? ????????\n" \
              ADDRESS2 ":   dce0ffff 646a0023 6e692f6b 6e726574 6c2f6c61 6564616f 614e2f72 65766974   " ASCII_1 "\n" \
              ADDRESS3 ":   7262694c 65697261 00000073 00000000                                       " ASCII_2 "\n"

#define PAT_8 ADDRESS1 ":   ???????????????? ???????????????? ???????????????? ????????????????\n" \
              ADDRESS2 ":   646a0023dce0ffff 6e7265746e692f6b 6564616f6c2f6c61 65766974614e2f72   " ASCII_1 "\n" \
              ADDRESS3 ":   656972617262694c 0000000000000073                                     " ASCII_2 "\n"
#else
#define PAT_2 ADDRESS1 ":   ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ???? ????\n" \
              ADDRESS2 ":   ffff e0dc 2300 6a64 6b2f 696e 7465 726e 616c 2f6c 6f61 6465 722f 4e61 7469 7665   " ASCII_1 "\n" \
              ADDRESS3 ":   4c69 6272 6172 6965 7300 0000 0000 0000                                           " ASCII_2 "\n"

#define PAT_4 ADDRESS1 ":   ???????? ???????? ???????? ???????? ???????? ???????? ???????? ????????\n" \
              ADDRESS2 ":   ffffe0dc 23006a64 6b2f696e 7465726e 616c2f6c 6f616465 722f4e61 74697665   " ASCII_1 "\n" \
              ADDRESS3 ":   4c696272 61726965 73000000 00000000                                       " ASCII_2 "\n"

#define PAT_8 ADDRESS1 ":   ???????????????? ???????????????? ???????????????? ????????????????\n" \
              ADDRESS2 ":   ffffe0dc23006a64 6b2f696e7465726e 616c2f6c6f616465 722f4e6174697665   " ASCII_1 "\n" \
              ADDRESS3 ":   4c69627261726965 7300000000000000                                     " ASCII_2 "\n"
#endif

  constexpr uint8_t bytes[] = {
    0xff, 0xff, 0xe0, 0xdc, 0x23, 0x00, 0x6a, 0x64, 0x6b, 0x2f, 0x69, 0x6e, 0x74, 0x65, 0x72, 0x6e,
    0x61, 0x6c, 0x2f, 0x6c, 0x6f, 0x61, 0x64, 0x65, 0x72, 0x2f, 0x4e, 0x61, 0x74, 0x69, 0x76, 0x65,
    0x4c, 0x69, 0x62, 0x72, 0x61, 0x72, 0x69, 0x65, 0x73, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
  };

  // two pages, first one protected.
  const size_t ps = os::vm_page_size();
  char* two_pages = os::reserve_memory(ps * 2, false, mtTest);
  os::commit_memory(two_pages, ps * 2, false);
  os::protect_memory(two_pages, ps, os::MEM_PROT_NONE, true);

  memcpy(two_pages + ps, bytes, sizeof(bytes));

  // print
  const const_address from = (const_address) two_pages + ps - 32;
  const const_address to = (const_address) from + 32 + sizeof(bytes);
  const const_address logical_start = (const_address) LP64_ONLY(0xAAAAAAAAAA00ULL) NOT_LP64(0xAAAAAA00ULL);

  do_test_print_hex_dump(from, to, 1, 32, logical_start, PAT_1);
  do_test_print_hex_dump(from, to, 2, 32, logical_start, PAT_2);
  do_test_print_hex_dump(from, to, 4, 32, logical_start, PAT_4);
  do_test_print_hex_dump(from, to, 8, 32, logical_start, PAT_8);

  // unaligned printing, should align to next lower unitsize
  do_test_print_hex_dump(from + 1, to, 2, 32, logical_start, PAT_2);
  do_test_print_hex_dump(from + 1, to, 4, 32, logical_start, PAT_4);
  do_test_print_hex_dump(from + 1, to, 8, 32, logical_start, PAT_8);

  // print with highlighted address
  do_test_print_hex_dump_highlighted(from, to, 1, 32, logical_start, PAT_HL_1A, from+5);
  do_test_print_hex_dump_highlighted(from, to, 1, 32, logical_start, PAT_HL_1B, from+32);
  do_test_print_hex_dump_highlighted(from, to, 1, 32, logical_start, PAT_HL_1B, from+60);
  do_test_print_hex_dump_highlighted(from, to, 2, 32, logical_start, PAT_HL_1C, from+60);

  os::release_memory(two_pages, ps * 2);
}
#endif // not AIX

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
  check_snprintf_result(0, 0, pf(nullptr, 0, "%s", ""), expect_count);
  check_snprintf_result(0, 0, pf(nullptr, 0, ""), expect_count);
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

#ifndef MAX_PATH
#define MAX_PATH    (2 * K)
#endif

TEST_VM(os, realpath) {
  // POSIX requires that the file exists; Windows tests for a valid drive letter
  // but may or may not test if the file exists. */
  static const char* nosuchpath = "/1234567890123456789";
  static const char* tmppath = "/tmp";

  char buffer[MAX_PATH];

  // Test a non-existant path, but provide a short buffer.
  errno = 0;
  const char* returnedBuffer = os::realpath(nosuchpath, buffer, sizeof(nosuchpath) - 2);
  // Reports ENOENT on Linux, ENAMETOOLONG on Windows.
  EXPECT_TRUE(returnedBuffer == nullptr);
#ifdef _WINDOWS
  EXPECT_TRUE(errno == ENAMETOOLONG);
#else
  EXPECT_TRUE(errno == ENOENT);
#endif

  // Test a non-existant path, but provide an adequate buffer.
  errno = 0;
  buffer[0] = 0;
  returnedBuffer = os::realpath(nosuchpath, buffer, sizeof(nosuchpath) + 3);
  // Reports ENOENT on Linux, may return 0 (and report an error) or buffer on some versions of Windows.
#ifdef _WINDOWS
  if (returnedBuffer != nullptr) {
    EXPECT_TRUE(returnedBuffer == buffer);
  } else {
    EXPECT_TRUE(errno != 0);
  }
#else
  EXPECT_TRUE(returnedBuffer == nullptr);
  EXPECT_TRUE(errno == ENOENT);
#endif

  // Test an existing path using a large buffer.
  errno = 0;
  returnedBuffer = os::realpath(tmppath, buffer, MAX_PATH);
  EXPECT_TRUE(returnedBuffer == buffer);

  // Test an existing path using a buffer that is too small on a normal macOS install.
  errno = 0;
  returnedBuffer = os::realpath(tmppath, buffer, strlen(tmppath) + 3);
  // On MacOS, /tmp is a symlink to /private/tmp, so doesn't fit in a small buffer.
#ifndef __APPLE__
  EXPECT_TRUE(returnedBuffer == buffer);
#else
  EXPECT_TRUE(returnedBuffer == nullptr);
  EXPECT_TRUE(errno == ENAMETOOLONG);
#endif

  // Test an existing path using a buffer that is too small.
  errno = 0;
  returnedBuffer = os::realpath(tmppath, buffer, strlen(tmppath) - 1);
  EXPECT_TRUE(returnedBuffer == nullptr);
  EXPECT_TRUE(errno == ENAMETOOLONG);

  // The following tests cause an assert inside os::realpath() in fastdebug mode:
#ifndef ASSERT
  errno = 0;
  returnedBuffer = os::realpath(nullptr, buffer, sizeof(buffer));
  EXPECT_TRUE(returnedBuffer == nullptr);
  EXPECT_TRUE(errno == EINVAL);

  errno = 0;
  returnedBuffer = os::realpath(tmppath, nullptr, sizeof(buffer));
  EXPECT_TRUE(returnedBuffer == nullptr);
  EXPECT_TRUE(errno == EINVAL);

  errno = 0;
  returnedBuffer = os::realpath(tmppath, buffer, 0);
  EXPECT_TRUE(returnedBuffer == nullptr);
  EXPECT_TRUE(errno == EINVAL);
#endif
}

#ifdef __APPLE__
// Not all macOS versions can use os::reserve_memory (i.e. anon_mmap) API
// to reserve executable memory, so before attempting to use it,
// we need to verify that we can do so by asking for a tiny executable
// memory chunk.
static inline bool can_reserve_executable_memory(void) {
  bool executable = true;
  size_t len = 128;
  char* p = os::reserve_memory(len, executable);
  bool exec_supported = (p != nullptr);
  if (exec_supported) {
    os::release_memory(p, len);
  }
  return exec_supported;
}
#endif

// Test that os::release_memory() can deal with areas containing multiple mappings.
#define PRINT_MAPPINGS(s) { tty->print_cr("%s", s); os::print_memory_mappings((char*)p, total_range_len, tty); tty->cr(); }
//#define PRINT_MAPPINGS

// Release a range allocated with reserve_multiple carefully, to not trip mapping
// asserts on Windows in os::release_memory()
static void carefully_release_multiple(address start, int num_stripes, size_t stripe_len) {
  for (int stripe = 0; stripe < num_stripes; stripe++) {
    address q = start + (stripe * stripe_len);
    EXPECT_TRUE(os::release_memory((char*)q, stripe_len));
  }
}

#ifndef _AIX // JDK-8257041
// Reserve an area consisting of multiple mappings
//  (from multiple calls to os::reserve_memory)
static address reserve_multiple(int num_stripes, size_t stripe_len) {
  assert(is_aligned(stripe_len, os::vm_allocation_granularity()), "Sanity");

#ifdef __APPLE__
  // Workaround: try reserving executable memory to figure out
  // if such operation is supported on this macOS version
  const bool exec_supported = can_reserve_executable_memory();
#endif

  address p = nullptr;
  for (int tries = 0; tries < 256 && p == nullptr; tries ++) {
    size_t total_range_len = num_stripes * stripe_len;
    // Reserve a large contiguous area to get the address space...
    p = (address)os::reserve_memory(total_range_len);
    EXPECT_NE(p, (address)nullptr);
    // .. release it...
    EXPECT_TRUE(os::release_memory((char*)p, total_range_len));
    // ... re-reserve in the same spot multiple areas...
    for (int stripe = 0; stripe < num_stripes; stripe++) {
      address q = p + (stripe * stripe_len);
      // Commit, alternatingly with or without exec permission,
      //  to prevent kernel from folding these mappings.
#ifdef __APPLE__
      const bool executable = exec_supported ? (stripe % 2 == 0) : false;
#else
      const bool executable = stripe % 2 == 0;
#endif
      q = (address)os::attempt_reserve_memory_at((char*)q, stripe_len, executable);
      if (q == nullptr) {
        // Someone grabbed that area concurrently. Cleanup, then retry.
        tty->print_cr("reserve_multiple: retry (%d)...", stripe);
        carefully_release_multiple(p, stripe, stripe_len);
        p = nullptr;
      } else {
        EXPECT_TRUE(os::commit_memory((char*)q, stripe_len, executable));
      }
    }
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
  EXPECT_NE(p, (address)nullptr);
  for (int stripe = 0; stripe < num_stripes; stripe++) {
    address q = p + (stripe * stripe_len);
    if (stripe % 2 == 0) {
      EXPECT_TRUE(os::commit_memory((char*)q, stripe_len, false));
    }
  }
  return p;
}

#ifdef _WIN32
struct NUMASwitcher {
  const bool _b;
  NUMASwitcher(bool v): _b(UseNUMAInterleaving) { UseNUMAInterleaving = v; }
  ~NUMASwitcher() { UseNUMAInterleaving = _b; }
};
#endif

#ifndef _AIX // JDK-8257041
TEST_VM(os, release_multi_mappings) {

  // With NMT enabled, this will trigger JDK-8263464. For now disable the test if NMT=on.
  if (MemTracker::tracking_level() > NMT_off) {
    return;
  }

  // Test that we can release an area created with multiple reservation calls
  // What we do:
  // A) we reserve 6 small segments (stripes) adjacent to each other. We commit
  //    them with alternating permissions to prevent the kernel from folding them into
  //    a single segment.
  //    -stripe-stripe-stripe-stripe-stripe-stripe-
  // B) we release the middle four stripes with a single os::release_memory call. This
  //    tests that os::release_memory indeed works across multiple segments created with
  //    multiple os::reserve calls.
  //    -stripe-___________________________-stripe-
  // C) Into the now vacated address range between the first and the last stripe, we
  //    re-reserve a new memory range. We expect this to work as a proof that the address
  //    range was really released by the single release call (B).
  //
  // Note that this is inherently racy. Between (B) and (C), some other thread may have
  //  reserved something into the hole in the meantime. Therefore we keep that range small and
  //  entrenched between the first and last stripe, which reduces the chance of some concurrent
  //  thread grabbing that memory.

  const size_t stripe_len = os::vm_allocation_granularity();
  const int num_stripes = 6;
  const size_t total_range_len = stripe_len * num_stripes;

  // reserve address space...
  address p = reserve_multiple(num_stripes, stripe_len);
  ASSERT_NE(p, (address)nullptr);
  PRINT_MAPPINGS("A");

  // .. release the middle stripes...
  address p_middle_stripes = p + stripe_len;
  const size_t middle_stripe_len = (num_stripes - 2) * stripe_len;
  {
    // On Windows, temporarily switch on UseNUMAInterleaving to allow release_memory to release
    //  multiple mappings in one go (otherwise we assert, which we test too, see death test below).
    WINDOWS_ONLY(NUMASwitcher b(true);)
    ASSERT_TRUE(os::release_memory((char*)p_middle_stripes, middle_stripe_len));
  }
  PRINT_MAPPINGS("B");

  // ...re-reserve the middle stripes. This should work unless release silently failed.
  address p2 = (address)os::attempt_reserve_memory_at((char*)p_middle_stripes, middle_stripe_len);

  ASSERT_EQ(p2, p_middle_stripes);

  PRINT_MAPPINGS("C");

  // Clean up. Release all mappings.
  {
    WINDOWS_ONLY(NUMASwitcher b(true);) // allow release_memory to release multiple regions
    ASSERT_TRUE(os::release_memory((char*)p, total_range_len));
  }
}
#endif // !AIX

#ifdef _WIN32
// On Windows, test that we recognize bad ranges.
//  On debug this would assert. Test that too.
//  On other platforms, we are unable to recognize bad ranges.
#ifdef ASSERT
TEST_VM_ASSERT_MSG(os, release_bad_ranges, ".*bad release") {
#else
TEST_VM(os, release_bad_ranges) {
#endif
  char* p = os::reserve_memory(4 * M);
  ASSERT_NE(p, (char*)nullptr);
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
  const size_t stripe_len = os::vm_allocation_granularity();
  const int num_stripes = 6;
  const size_t total_range_len = stripe_len * num_stripes;

  // reserve address space...
  address p = reserve_one_commit_multiple(num_stripes, stripe_len);
  PRINT_MAPPINGS("A");
  ASSERT_NE(p, (address)nullptr);

  // // make things even more difficult by trying to reserve at the border of the region
  address border = p + num_stripes * stripe_len;
  address p2 = (address)os::attempt_reserve_memory_at((char*)border, stripe_len);
  PRINT_MAPPINGS("B");

  ASSERT_TRUE(p2 == nullptr || p2 == border);

  ASSERT_TRUE(os::release_memory((char*)p, total_range_len));
  PRINT_MAPPINGS("C");

  if (p2 != nullptr) {
    ASSERT_TRUE(os::release_memory((char*)p2, stripe_len));
    PRINT_MAPPINGS("D");
  }
}

static void test_show_mappings(address start, size_t size) {
  // Note: should this overflow, thats okay. stream will silently truncate. Does not matter for the test.
  const size_t buflen = 4 * M;
  char* buf = NEW_C_HEAP_ARRAY(char, buflen, mtInternal);
  buf[0] = '\0';
  stringStream ss(buf, buflen);
  if (start != nullptr) {
    os::print_memory_mappings((char*)start, size, &ss);
  } else {
    os::print_memory_mappings(&ss); // prints full address space
  }
  // Still an empty implementation on MacOS and AIX
#if defined(LINUX) || defined(_WIN32)
  EXPECT_NE(buf[0], '\0');
#endif
  // buf[buflen - 1] = '\0';
  // tty->print_raw(buf);
  FREE_C_HEAP_ARRAY(char, buf);
}

TEST_VM(os, show_mappings_small_range) {
  test_show_mappings((address)0x100000, 2 * G);
}

TEST_VM(os, show_mappings_full_range) {
  // Reserve a small range and fill it with a marker string, should show up
  // on implementations displaying range snippets
  char* p = os::reserve_memory(1 * M, false, mtInternal);
  if (p != nullptr) {
    if (os::commit_memory(p, 1 * M, false)) {
      strcpy(p, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    }
  }
  test_show_mappings(nullptr, 0);
  if (p != nullptr) {
    os::release_memory(p, 1 * M);
  }
}

#ifdef _WIN32
// Test os::win32::find_mapping
TEST_VM(os, find_mapping_simple) {
  const size_t total_range_len = 4 * M;
  os::win32::mapping_info_t mapping_info;

  // Some obvious negatives
  ASSERT_FALSE(os::win32::find_mapping((address)nullptr, &mapping_info));
  ASSERT_FALSE(os::win32::find_mapping((address)4711, &mapping_info));

  // A simple allocation
  {
    address p = (address)os::reserve_memory(total_range_len);
    ASSERT_NE(p, (address)nullptr);
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
  ASSERT_NE(p, (address)nullptr);
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
    ASSERT_NE(p, (address)nullptr);
    PRINT_MAPPINGS("E");
    for (int stripe = 0; stripe < 4; stripe++) {
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

TEST_VM(os, os_pagesizes) {
  ASSERT_EQ(os::min_page_size(), 4 * K);
  ASSERT_LE(os::min_page_size(), os::vm_page_size());
  // The vm_page_size should be the smallest in the set of allowed page sizes
  // (contract says "default" page size but a lot of code actually assumes
  //  this to be the smallest page size; notable, deliberate exception is
  //  AIX which can have smaller page sizes but those are not part of the
  //  page_sizes() set).
  ASSERT_EQ(os::page_sizes().smallest(), os::vm_page_size());
  // The large page size, if it exists, shall be part of the set
  if (UseLargePages) {
    ASSERT_GT(os::large_page_size(), os::vm_page_size());
    ASSERT_TRUE(os::page_sizes().contains(os::large_page_size()));
  }
  os::page_sizes().print_on(tty);
  tty->cr();
}

static const int min_page_size_log2 = exact_log2(os::min_page_size());
static const int max_page_size_log2 = (int)BitsPerWord;

TEST_VM(os, pagesizes_test_range) {
  for (int bit = min_page_size_log2; bit < max_page_size_log2; bit++) {
    for (int bit2 = min_page_size_log2; bit2 < max_page_size_log2; bit2++) {
      const size_t s =  (size_t)1 << bit;
      const size_t s2 = (size_t)1 << bit2;
      os::PageSizes pss;
      ASSERT_EQ((size_t)0, pss.smallest());
      ASSERT_EQ((size_t)0, pss.largest());
      // one size set
      pss.add(s);
      ASSERT_TRUE(pss.contains(s));
      ASSERT_EQ(s, pss.smallest());
      ASSERT_EQ(s, pss.largest());
      ASSERT_EQ(pss.next_larger(s), (size_t)0);
      ASSERT_EQ(pss.next_smaller(s), (size_t)0);
      // two set
      pss.add(s2);
      ASSERT_TRUE(pss.contains(s2));
      if (s2 < s) {
        ASSERT_EQ(s2, pss.smallest());
        ASSERT_EQ(s, pss.largest());
        ASSERT_EQ(pss.next_larger(s2), (size_t)s);
        ASSERT_EQ(pss.next_smaller(s2), (size_t)0);
        ASSERT_EQ(pss.next_larger(s), (size_t)0);
        ASSERT_EQ(pss.next_smaller(s), (size_t)s2);
      } else if (s2 > s) {
        ASSERT_EQ(s, pss.smallest());
        ASSERT_EQ(s2, pss.largest());
        ASSERT_EQ(pss.next_larger(s), (size_t)s2);
        ASSERT_EQ(pss.next_smaller(s), (size_t)0);
        ASSERT_EQ(pss.next_larger(s2), (size_t)0);
        ASSERT_EQ(pss.next_smaller(s2), (size_t)s);
      }
      for (int bit3 = min_page_size_log2; bit3 < max_page_size_log2; bit3++) {
        const size_t s3 = (size_t)1 << bit3;
        ASSERT_EQ(s3 == s || s3 == s2, pss.contains(s3));
      }
    }
  }
}

TEST_VM(os, pagesizes_test_print) {
  os::PageSizes pss;
  const size_t sizes[] = { 16 * K, 64 * K, 128 * K, 1 * M, 4 * M, 1 * G, 2 * G, 0 };
  static const char* const expected = "16k, 64k, 128k, 1M, 4M, 1G, 2G";
  for (int i = 0; sizes[i] != 0; i++) {
    pss.add(sizes[i]);
  }
  char buffer[256];
  stringStream ss(buffer, sizeof(buffer));
  pss.print_on(&ss);
  EXPECT_STREQ(expected, buffer);
}

TEST_VM(os, dll_address_to_function_and_library_name) {
  char tmp[1024];
  char output[1024];
  stringStream st(output, sizeof(output));

#define EXPECT_CONTAINS(haystack, needle) \
  EXPECT_THAT(haystack, HasSubstr(needle));
#define EXPECT_DOES_NOT_CONTAIN(haystack, needle) \
  EXPECT_THAT(haystack, Not(HasSubstr(needle)));
// #define LOG(...) tty->print_cr(__VA_ARGS__); // enable if needed
#define LOG(...)

  // Invalid addresses
  LOG("os::print_function_and_library_name(st, -1) expects FALSE.");
  address addr = (address)(intptr_t)-1;
  EXPECT_FALSE(os::print_function_and_library_name(&st, addr));
  LOG("os::print_function_and_library_name(st, nullptr) expects FALSE.");
  addr = nullptr;
  EXPECT_FALSE(os::print_function_and_library_name(&st, addr));

  // Valid addresses
  // Test with or without shorten-paths, demangle, and scratch buffer
  for (int i = 0; i < 16; i++) {
    const bool shorten_paths = (i & 1) != 0;
    const bool demangle = (i & 2) != 0;
    const bool strip_arguments = (i & 4) != 0;
    const bool provide_scratch_buffer = (i & 8) != 0;
    LOG("shorten_paths=%d, demangle=%d, strip_arguments=%d, provide_scratch_buffer=%d",
        shorten_paths, demangle, strip_arguments, provide_scratch_buffer);

    // Should show os::min_page_size in libjvm
    addr = CAST_FROM_FN_PTR(address, Threads::create_vm);
    st.reset();
    EXPECT_TRUE(os::print_function_and_library_name(&st, addr,
                                                    provide_scratch_buffer ? tmp : nullptr,
                                                    sizeof(tmp),
                                                    shorten_paths, demangle,
                                                    strip_arguments));
    EXPECT_CONTAINS(output, "Threads");
    EXPECT_CONTAINS(output, "create_vm");
    EXPECT_CONTAINS(output, "jvm"); // "jvm.dll" or "libjvm.so" or similar
    LOG("%s", output);

    // Test truncation on scratch buffer
    if (provide_scratch_buffer) {
      st.reset();
      tmp[10] = 'X';
      EXPECT_TRUE(os::print_function_and_library_name(&st, addr, tmp, 10,
                                                      shorten_paths, demangle));
      EXPECT_EQ(tmp[10], 'X');
      LOG("%s", output);
    }
  }
}

// Not a regex! Very primitive, just match:
// "d" - digit
// "a" - ascii
// "." - everything
// rest must match
static bool very_simple_string_matcher(const char* pattern, const char* s) {
  const size_t lp = strlen(pattern);
  const size_t ls = strlen(s);
  if (ls < lp) {
    return false;
  }
  for (size_t i = 0; i < lp; i ++) {
    switch (pattern[i]) {
      case '.': continue;
      case 'd': if (!isdigit(s[i])) return false; break;
      case 'a': if (!isascii(s[i])) return false; break;
      default: if (s[i] != pattern[i]) return false; break;
    }
  }
  return true;
}

TEST_VM(os, iso8601_time) {
  char buffer[os::iso8601_timestamp_size + 1]; // + space for canary
  buffer[os::iso8601_timestamp_size] = 'X'; // canary
  const char* result = nullptr;
  // YYYY-MM-DDThh:mm:ss.mmm+zzzz
  const char* const pattern_utc = "dddd-dd-dd.dd:dd:dd.ddd.0000";
  const char* const pattern_local = "dddd-dd-dd.dd:dd:dd.ddd.dddd";

  result = os::iso8601_time(buffer, sizeof(buffer), true);
  tty->print_cr("%s", result);
  EXPECT_EQ(result, buffer);
  EXPECT_TRUE(very_simple_string_matcher(pattern_utc, result));

  result = os::iso8601_time(buffer, sizeof(buffer), false);
  tty->print_cr("%s", result);
  EXPECT_EQ(result, buffer);
  EXPECT_TRUE(very_simple_string_matcher(pattern_local, result));

  // Test with explicit timestamps
  result = os::iso8601_time(0, buffer, sizeof(buffer), true);
  tty->print_cr("%s", result);
  EXPECT_EQ(result, buffer);
  EXPECT_TRUE(very_simple_string_matcher("1970-01-01.00:00:00.000+0000", result));

  result = os::iso8601_time(17, buffer, sizeof(buffer), true);
  tty->print_cr("%s", result);
  EXPECT_EQ(result, buffer);
  EXPECT_TRUE(very_simple_string_matcher("1970-01-01.00:00:00.017+0000", result));

  // Canary should still be intact
  EXPECT_EQ(buffer[os::iso8601_timestamp_size], 'X');
}

TEST_VM(os, is_first_C_frame) {
#if !defined(_WIN32) && !defined(ZERO) && !defined(__thumb__)
  frame invalid_frame;
  EXPECT_TRUE(os::is_first_C_frame(&invalid_frame)); // the frame has zeroes for all values

  frame cur_frame = os::current_frame(); // this frame has to have a sender
  EXPECT_FALSE(os::is_first_C_frame(&cur_frame));
#endif // _WIN32
}

#ifdef __GLIBC__
TEST_VM(os, trim_native_heap) {
  EXPECT_TRUE(os::can_trim_native_heap());
  os::size_change_t sc;
  sc.before = sc.after = (size_t)-1;
  EXPECT_TRUE(os::trim_native_heap(&sc));
  tty->print_cr(SIZE_FORMAT "->" SIZE_FORMAT, sc.before, sc.after);
  // Regardless of whether we freed memory, both before and after
  // should be somewhat believable numbers (RSS).
  const size_t min = 5 * M;
  const size_t max = LP64_ONLY(20 * G) NOT_LP64(3 * G);
  ASSERT_LE(min, sc.before);
  ASSERT_GT(max, sc.before);
  ASSERT_LE(min, sc.after);
  ASSERT_GT(max, sc.after);
  // Should also work
  EXPECT_TRUE(os::trim_native_heap());
}
#else
TEST_VM(os, trim_native_heap) {
  EXPECT_FALSE(os::can_trim_native_heap());
}
#endif // __GLIBC__

TEST_VM(os, open_O_CLOEXEC) {
#if !defined(_WIN32)
  int fd = os::open("test_file.txt", O_RDWR | O_CREAT | O_TRUNC, 0666); // open will use O_CLOEXEC
  EXPECT_TRUE(fd > 0);
  int flags = ::fcntl(fd, F_GETFD);
  EXPECT_TRUE((flags & FD_CLOEXEC) != 0); // if O_CLOEXEC worked, then FD_CLOEXEC should be ON
  ::close(fd);
#endif
}

TEST_VM(os, reserve_at_wish_address_shall_not_replace_mappings_smallpages) {
  char* p1 = os::reserve_memory(M, false, mtTest);
  ASSERT_NE(p1, nullptr);
  char* p2 = os::attempt_reserve_memory_at(p1, M);
  ASSERT_EQ(p2, nullptr); // should have failed
  os::release_memory(p1, M);
}

TEST_VM(os, reserve_at_wish_address_shall_not_replace_mappings_largepages) {
  if (UseLargePages && !os::can_commit_large_page_memory()) { // aka special
    const size_t lpsz = os::large_page_size();
    char* p1 = os::reserve_memory_aligned(lpsz, lpsz, false);
    ASSERT_NE(p1, nullptr);
    char* p2 = os::reserve_memory_special(lpsz, lpsz, lpsz, p1, false);
    ASSERT_EQ(p2, nullptr); // should have failed
    os::release_memory(p1, M);
  } else {
    tty->print_cr("Skipped.");
  }
}

TEST_VM(os, vm_min_address) {
  size_t s = os::vm_min_address();
  ASSERT_GE(s, M);
  // Test upper limit. On Linux, its adjustable, so we just test for absurd values to prevent errors
  // with high vm.mmap_min_addr settings.
#if defined(_LP64)
  ASSERT_LE(s, NOT_LINUX(G * 4) LINUX_ONLY(G * 1024));
#endif
}

#if !defined(_WINDOWS) && !defined(_AIX)
TEST_VM(os, free_without_uncommit) {
  const size_t page_sz = os::vm_page_size();
  const size_t pages = 64;
  const size_t size = pages * page_sz;

  char* base = os::reserve_memory(size, false, mtTest);
  ASSERT_NE(base, (char*) nullptr);
  ASSERT_TRUE(os::commit_memory(base, size, false));

  for (size_t index = 0; index < pages; index++) {
    base[index * page_sz] = 'a';
  }

  os::disclaim_memory(base, size);

  // Ensure we can still use the memory without having to recommit.
  for (size_t index = 0; index < pages; index++) {
    base[index * page_sz] = 'a';
  }

  os::release_memory(base, size);
}
#endif
