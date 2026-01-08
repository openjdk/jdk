/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifdef LINUX

#include "procMapsParser.hpp"
#include "unittest.hpp"

#include <stdio.h>

TEST(ProcSmapsParserTest, ParseMappings) {
  const char* smaps_content =
    "7f5a00000000-7f5a00001000 r--p 00000000 00:00 0                          [anon]\n"
    "Size:                  4 kB\n"
    "KernelPageSize:        4 kB\n"
    "MMUPageSize:           4 kB\n"
    "Rss:                   0 kB\n"
    "Pss:                   0 kB\n"
    "Shared_Clean:          0 kB\n"
    "Shared_Dirty:          0 kB\n"
    "Private_Clean:         0 kB\n"
    "Private_Dirty:         0 kB\n"
    "Referenced:            0 kB\n"
    "Anonymous:             0 kB\n"
    "LazyFree:              0 kB\n"
    "AnonHugePages:         0 kB\n"
    "ShmemPmdMapped:        0 kB\n"
    "FilePmdMapped:         0 kB\n"
    "Shared_Hugetlb:        0 kB\n"
    "Private_Hugetlb:       0 kB\n"
    "Swap:                  0 kB\n"
    "SwapPss:               0 kB\n"
    "Locked:                0 kB\n"
    "THPeligible:    0\n"
    "VmFlags: rd mr mw me ac \n"
    "7f5a00001000-7f5a00002000 rw-p 00000000 00:00 0                          [anon]\n"
    "Size:                  4 kB\n"
    "KernelPageSize:        4 kB\n"
    "MMUPageSize:           4 kB\n"
    "Rss:                   4 kB\n"
    "Pss:                   4 kB\n"
    "Shared_Clean:          0 kB\n"
    "Shared_Dirty:          0 kB\n"
    "Private_Clean:         0 kB\n"
    "Private_Dirty:         4 kB\n"
    "Referenced:            4 kB\n"
    "Anonymous:             4 kB\n"
    "LazyFree:              0 kB\n"
    "AnonHugePages:         0 kB\n"
    "ShmemPmdMapped:        0 kB\n"
    "FilePmdMapped:         0 kB\n"
    "Shared_Hugetlb:        0 kB\n"
    "Private_Hugetlb:       0 kB\n"
    "Swap:                  0 kB\n"
    "SwapPss:               0 kB\n"
    "Locked:                0 kB\n"
    "THPeligible:    0\n"
    "VmFlags: rd wr mr mw me ac \n";

  FILE* f = fmemopen((void*)smaps_content, strlen(smaps_content), "r");
  ASSERT_TRUE(f != nullptr);

  ProcSmapsParser parser(f);
  ProcSmapsInfo info;

  // First mapping
  ASSERT_TRUE(parser.parse_next(info));
  EXPECT_EQ((uintptr_t)info.from, 0x7f5a00000000ULL);
  EXPECT_EQ((uintptr_t)info.to, 0x7f5a00001000ULL);
  EXPECT_STREQ(info.prot, "r--p");
  EXPECT_TRUE(info.rd);
  EXPECT_FALSE(info.wr);

  // Second mapping
  ASSERT_TRUE(parser.parse_next(info));
  EXPECT_EQ((uintptr_t)info.from, 0x7f5a00001000ULL);
  EXPECT_EQ((uintptr_t)info.to, 0x7f5a00002000ULL);
  EXPECT_STREQ(info.prot, "rw-p");
  EXPECT_TRUE(info.rd);
  EXPECT_TRUE(info.wr);

  // End of file
  ASSERT_FALSE(parser.parse_next(info));

  fclose(f);
}

#endif // LINUX
