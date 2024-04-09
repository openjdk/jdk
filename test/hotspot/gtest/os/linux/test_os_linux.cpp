/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifdef LINUX
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"
#include "os_linux.hpp"

class OSLinuxTestFixture : public testing::Test {
public:
  void call_parse_kernel_version(long* major, long* minor, char* release) {
    os::Linux::parse_kernel_version(major, minor, release);
  }
};

TEST_F(OSLinuxTestFixture, ParseKernelVersion) {
  // We need some memory that will SIGSEGV if read to.
  // os::reserve_memory fits the bill, as it does PROT_NONE.
  const size_t res_memory_size = os::vm_page_size();
  char* res_memory = os::reserve_memory(res_memory_size, false, mtTest);
  long minor = -1;
  long major = -1;
  auto test = [&](const char* str, long majorexp, long minorexp) {
    // Remove old string and make all memory PROT_NONE again.
    os::uncommit_memory(res_memory, res_memory_size);
    // Write new string.
    size_t strsz = strlen(str) + 1;
    os::commit_memory(res_memory, strsz , false);
    ::memcpy(res_memory, str, strsz);
    minor = -1; major = -1;
    call_parse_kernel_version(&major, &minor, res_memory);
    EXPECT_EQ(majorexp, major);
    EXPECT_EQ(minorexp, minor);
  };

  // Expected
  test("2.6", 2, 6);
  test("2.6.1", 2, 6);
  // Unexpected
  test("abc", -1, -1);
  test("2.", 2, -1);
  test("a.5", 5, -1);
  os::release_memory(res_memory, res_memory_size);
}


#endif // LINUX
