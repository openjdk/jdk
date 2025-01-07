/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"

#include <type_traits>

static ::testing::AssertionResult testPageAddress(
  const char* expected_addr_expr,
  const char* addr_expr,
  const char* page_addr_expr,
  const char* page_size_expr,
  const char* actual_addr_expr,
  address expected_addr,
  address addr,
  address page_addr,
  intptr_t page_size,
  address actual_addr) {
  if (expected_addr == actual_addr) {
    return ::testing::AssertionSuccess();
  }

  return ::testing::AssertionFailure()
    << actual_addr_expr << " returned unexpected address " << (void*) actual_addr << std::endl
    << "Expected " << expected_addr_expr << ": " << (void*) expected_addr << std::endl
    << "where" << std::endl
    << addr_expr << ": " << (void*) addr << std::endl
    << page_addr_expr << ": " << (void*) page_addr << std::endl
    << page_size_expr << ": " << page_size;
}

TEST_VM(globalDefinitions, clamp_address_in_page) {
  const intptr_t page_sizes[] = {static_cast<intptr_t>(os::vm_page_size()), 4096, 8192, 65536, 2 * 1024 * 1024};
  const int num_page_sizes = sizeof(page_sizes) / sizeof(page_sizes[0]);

  for (int i = 0; i < num_page_sizes; i++) {
    intptr_t page_size = page_sizes[i];
    address page_address = (address) (10 * page_size);

    const intptr_t within_page_offsets[] = {0, 128, page_size - 1};
    const int num_within_page_offsets = sizeof(within_page_offsets) / sizeof(within_page_offsets[0]);

    for (int k = 0; k < num_within_page_offsets; ++k) {
      address addr = page_address + within_page_offsets[k];
      address expected_address = addr;
      EXPECT_PRED_FORMAT5(testPageAddress, expected_address, addr, page_address, page_size,
                          clamp_address_in_page(addr, page_address, page_size))
        << "Expect that address within page is returned as is";
    }

    const intptr_t above_page_offsets[] = {page_size, page_size + 1, 5 * page_size + 1};
    const int num_above_page_offsets = sizeof(above_page_offsets) / sizeof(above_page_offsets[0]);

    for (int k = 0; k < num_above_page_offsets; ++k) {
      address addr = page_address + above_page_offsets[k];
      address expected_address = page_address + page_size;
      EXPECT_PRED_FORMAT5(testPageAddress, expected_address, addr, page_address, page_size,
                          clamp_address_in_page(addr, page_address, page_size))
        << "Expect that address above page returns start of next page";
    }

    const intptr_t below_page_offsets[] = {1, 2 * page_size + 1, 5 * page_size + 1};
    const int num_below_page_offsets = sizeof(below_page_offsets) / sizeof(below_page_offsets[0]);

    for (int k = 0; k < num_below_page_offsets; ++k) {
      address addr = page_address - below_page_offsets[k];
      address expected_address = page_address;
      EXPECT_PRED_FORMAT5(testPageAddress, expected_address, addr, page_address, page_size,
                          clamp_address_in_page(addr, page_address, page_size))
        << "Expect that address below page returns start of page";
    }
  }
}

TEST(globalDefinitions, proper_unit) {
  EXPECT_EQ(0u,     byte_size_in_proper_unit(0u));
  EXPECT_STREQ("B", proper_unit_for_byte_size(0u));

  EXPECT_EQ(1u,     byte_size_in_proper_unit(1u));
  EXPECT_STREQ("B", proper_unit_for_byte_size(1u));

  EXPECT_EQ(1023u,  byte_size_in_proper_unit(K - 1));
  EXPECT_STREQ("B", proper_unit_for_byte_size(K - 1));

  EXPECT_EQ(1024u,  byte_size_in_proper_unit(K));
  EXPECT_STREQ("B", proper_unit_for_byte_size(K));

  EXPECT_EQ(1025u,  byte_size_in_proper_unit(K + 1));
  EXPECT_STREQ("B", proper_unit_for_byte_size(K + 1));

  EXPECT_EQ(51200u, byte_size_in_proper_unit(50*K));
  EXPECT_STREQ("B", proper_unit_for_byte_size(50*K));

  EXPECT_EQ(1023u,  byte_size_in_proper_unit(M - 1));
  EXPECT_STREQ("K", proper_unit_for_byte_size(M - 1));

  EXPECT_EQ(1024u,  byte_size_in_proper_unit(M));
  EXPECT_STREQ("K", proper_unit_for_byte_size(M));

  EXPECT_EQ(1024u,  byte_size_in_proper_unit(M + 1));
  EXPECT_STREQ("K", proper_unit_for_byte_size(M + 1));

  EXPECT_EQ(1025u,  byte_size_in_proper_unit(M + K));
  EXPECT_STREQ("K", proper_unit_for_byte_size(M + K));

  EXPECT_EQ(51200u, byte_size_in_proper_unit(50*M));
  EXPECT_STREQ("K", proper_unit_for_byte_size(50*M));

#ifdef _LP64
  EXPECT_EQ(1023u,  byte_size_in_proper_unit(G - 1));
  EXPECT_STREQ("M", proper_unit_for_byte_size(G - 1));

  EXPECT_EQ(1024u,  byte_size_in_proper_unit(G));
  EXPECT_STREQ("M", proper_unit_for_byte_size(G));

  EXPECT_EQ(1024u,  byte_size_in_proper_unit(G + 1));
  EXPECT_STREQ("M", proper_unit_for_byte_size(G + 1));

  EXPECT_EQ(1024u,  byte_size_in_proper_unit(G + K));
  EXPECT_STREQ("M", proper_unit_for_byte_size(G + K));

  EXPECT_EQ(1025u,  byte_size_in_proper_unit(G + M));
  EXPECT_STREQ("M", proper_unit_for_byte_size(G + M));

  EXPECT_EQ(51200u, byte_size_in_proper_unit(50*G));
  EXPECT_STREQ("M", proper_unit_for_byte_size(50*G));
#endif
}

TEST(globalDefinitions, exact_unit_for_byte_size) {
  EXPECT_STREQ("B", exact_unit_for_byte_size(0));
  EXPECT_STREQ("B", exact_unit_for_byte_size(1));
  EXPECT_STREQ("B", exact_unit_for_byte_size(K - 1));
  EXPECT_STREQ("K", exact_unit_for_byte_size(K));
  EXPECT_STREQ("B", exact_unit_for_byte_size(K + 1));
  EXPECT_STREQ("B", exact_unit_for_byte_size(M - 1));
  EXPECT_STREQ("M", exact_unit_for_byte_size(M));
  EXPECT_STREQ("B", exact_unit_for_byte_size(M + 1));
  EXPECT_STREQ("K", exact_unit_for_byte_size(M + K));
#ifdef _LP64
  EXPECT_STREQ("B", exact_unit_for_byte_size(G - 1));
  EXPECT_STREQ("G", exact_unit_for_byte_size(G));
  EXPECT_STREQ("B", exact_unit_for_byte_size(G + 1));
  EXPECT_STREQ("K", exact_unit_for_byte_size(G + K));
  EXPECT_STREQ("M", exact_unit_for_byte_size(G + M));
  EXPECT_STREQ("K", exact_unit_for_byte_size(G + M + K));
#endif
}

TEST(globalDefinitions, byte_size_in_exact_unit) {
  EXPECT_EQ(0u, byte_size_in_exact_unit(0));
  EXPECT_EQ(1u, byte_size_in_exact_unit(1));
  EXPECT_EQ(K - 1, byte_size_in_exact_unit(K - 1));
  EXPECT_EQ(1u, byte_size_in_exact_unit(K));
  EXPECT_EQ(K + 1, byte_size_in_exact_unit(K + 1));
  EXPECT_EQ(M - 1, byte_size_in_exact_unit(M - 1));
  EXPECT_EQ(1u, byte_size_in_exact_unit(M));
  EXPECT_EQ(M + 1, byte_size_in_exact_unit(M + 1));
  EXPECT_EQ(K + 1, byte_size_in_exact_unit(M + K));
#ifdef _LP64
  EXPECT_EQ(G - 1, byte_size_in_exact_unit(G - 1));
  EXPECT_EQ(1u, byte_size_in_exact_unit(G));
  EXPECT_EQ(G + 1, byte_size_in_exact_unit(G + 1));
  EXPECT_EQ(M + 1, byte_size_in_exact_unit(G + K));
  EXPECT_EQ(K + 1, byte_size_in_exact_unit(G + M));
  EXPECT_EQ(M + K + 1, byte_size_in_exact_unit(G + M + K));
#endif
}

TEST(globalDefinitions, array_size) {
  const size_t test_size = 10;

  {
    int test_array[test_size] = {};
    static_assert(test_size == ARRAY_SIZE(test_array), "must be");
  }

  {
    double test_array[test_size] = {};
    static_assert(test_size == ARRAY_SIZE(test_array), "must be");
  }

  struct ArrayElt { int x; };

  {
    ArrayElt test_array[test_size] = {};
    static_assert(test_size == ARRAY_SIZE(test_array), "must be");
  }

  {
    const ArrayElt test_array[] = { {0}, {1}, {2}, {3}, {4}, {5} };
    static_assert(6 == ARRAY_SIZE(test_array), "must be");
  }

}

#define check_format(format, value, expected)                  \
  do {                                                         \
    ResourceMark rm;                                           \
    stringStream out;                                          \
    out.print((format), (value));                              \
    const char* result = out.as_string();                      \
    EXPECT_STREQ((result), (expected)) << "Failed with"        \
        << " format '"   << (format)   << "'"                  \
        << " value '"    << (value);                           \
  } while (false)

TEST(globalDefinitions, format_specifiers) {
  check_format(INT8_FORMAT_X_0,        (int8_t)0x01,      "0x01");
  check_format(UINT8_FORMAT_X_0,       (uint8_t)0x01u,    "0x01");

  check_format(INT16_FORMAT_X_0,       (int16_t)0x0123,   "0x0123");
  check_format(UINT16_FORMAT_X_0,      (uint16_t)0x0123u, "0x0123");

  check_format(INT32_FORMAT,           123,               "123");
  check_format(INT32_FORMAT_X,         0x123,             "0x123");
  check_format(INT32_FORMAT_X_0,       0x123,             "0x00000123");
  check_format(INT32_FORMAT_W(5),      123,               "  123");
  check_format(INT32_FORMAT_W(-5),     123,               "123  ");
  check_format(UINT32_FORMAT,          123u,              "123");
  check_format(UINT32_FORMAT_X,        0x123u,            "0x123");
  check_format(UINT32_FORMAT_X_0,      0x123u,            "0x00000123");
  check_format(UINT32_FORMAT_W(5),     123u,              "  123");
  check_format(UINT32_FORMAT_W(-5),    123u,              "123  ");

  check_format(INT64_FORMAT,           (int64_t)123,      "123");
  check_format(INT64_FORMAT_X,         (int64_t)0x123,    "0x123");
  check_format(INT64_FORMAT_X_0,       (int64_t)0x123,    "0x0000000000000123");
  check_format(INT64_FORMAT_W(5),      (int64_t)123,      "  123");
  check_format(INT64_FORMAT_W(-5),     (int64_t)123,      "123  ");

  check_format(UINT64_FORMAT,          (uint64_t)123,     "123");
  check_format(UINT64_FORMAT_X,        (uint64_t)0x123,   "0x123");
  check_format(UINT64_FORMAT_X_0,      (uint64_t)0x123,   "0x0000000000000123");
  check_format(UINT64_FORMAT_W(5),     (uint64_t)123,     "  123");
  check_format(UINT64_FORMAT_W(-5),    (uint64_t)123,     "123  ");

  check_format(SSIZE_FORMAT,           (ssize_t)123,      "123");
  check_format(SSIZE_FORMAT,           (ssize_t)-123,     "-123");
  check_format(SSIZE_FORMAT,           (ssize_t)2147483647, "2147483647");
  check_format(SSIZE_FORMAT,           (ssize_t)-2147483647, "-2147483647");
  check_format(SSIZE_PLUS_FORMAT,      (ssize_t)123,      "+123");
  check_format(SSIZE_PLUS_FORMAT,      (ssize_t)-123,     "-123");
  check_format(SSIZE_PLUS_FORMAT,      (ssize_t)2147483647, "+2147483647");
  check_format(SSIZE_PLUS_FORMAT,      (ssize_t)-2147483647, "-2147483647");
  check_format(SSIZE_FORMAT_W(5),      (ssize_t)123,      "  123");
  check_format(SSIZE_FORMAT_W(-5),     (ssize_t)123,      "123  ");
  check_format(SIZE_FORMAT,            (size_t)123u,      "123");
  check_format(SIZE_FORMAT_X,          (size_t)0x123u,    "0x123");
  check_format(SIZE_FORMAT_X_0,        (size_t)0x123u,    "0x" LP64_ONLY("00000000") "00000123");
  check_format(SIZE_FORMAT_W(5),       (size_t)123u,      "  123");
  check_format(SIZE_FORMAT_W(-5),      (size_t)123u,      "123  ");

  check_format(INTX_FORMAT,            (intx)123,         "123");
  check_format(INTX_FORMAT_X,          (intx)0x123,       "0x123");
  check_format(INTX_FORMAT_W(5),       (intx)123,         "  123");
  check_format(INTX_FORMAT_W(-5),      (intx)123,         "123  ");

  check_format(UINTX_FORMAT,           (uintx)123u,       "123");
  check_format(UINTX_FORMAT_X,         (uintx)0x123u,     "0x123");
  check_format(UINTX_FORMAT_W(5),      (uintx)123u,       "  123");
  check_format(UINTX_FORMAT_W(-5),     (uintx)123u,       "123  ");

  check_format(INTPTR_FORMAT,          (intptr_t)0x123,   "0x" LP64_ONLY("00000000") "00000123");
  check_format(PTR_FORMAT,             (uintptr_t)0x123,  "0x" LP64_ONLY("00000000") "00000123");

  // Check all platforms print this compatibly without leading 0x.
  check_format(UINT64_FORMAT_0,        (u8)0x123,         "0000000000000123");
}
