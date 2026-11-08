/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifdef LINUX

#include "gc/z/zArray.inline.hpp"
#include "gc/z/zMountPoint_linux.hpp"
#include "gc/z/zPhysicalMemoryBacking_linux.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/autoRestore.hpp"
#include "unittest.hpp"

class ZMountPointTest {
  static void check(const ZMountPoint& mountpoint, const char* filesystem,
                    const char** preferred, const char* const* available,
                    const char* expected) {
    ZArray<char*> mountpoints;
    for (const char* const* p = available; *p != nullptr; ++p) {
      mountpoints.append(const_cast<char*>(*p));
    }

    char* const result = mountpoint.find_preferred_mountpoint(filesystem, &mountpoints, preferred);
    EXPECT_STREQ(expected, result);
    os::free(result);
  }

public:
  static void test(const char* filesystem, const char* first, const char* second) {
    // Avoid consulting the host's mount table during construction
    AutoModifyRestore<ccstr> path(AllocateHeapAt, "unused-for-mountpoint-test");
    const char* preferred[] = { first, second, nullptr };
    ZMountPoint mountpoint(filesystem, preferred);

    const char* forward[] = { first, second, nullptr };
    check(mountpoint, filesystem, preferred, forward, first);

    const char* reverse[] = { second, first, nullptr };
    check(mountpoint, filesystem, preferred, reverse, first);

    const char* second_only[] = { "/unrelated", second, nullptr };
    check(mountpoint, filesystem, preferred, second_only, second);

    const char* mixed[] = { "/unrelated", second, first, nullptr };
    check(mountpoint, filesystem, preferred, mixed, first);

    const char* unmatched[] = { "/unrelated", "/another", nullptr };
    check(mountpoint, filesystem, preferred, unmatched, nullptr);
  }
};

TEST_OTHER_VM(ZMountPoint, preferred_tmpfs_order) {
  ZMountPointTest::test("tmpfs", ZPreferredTmpfsMountpoints[0], ZPreferredTmpfsMountpoints[1]);
}

TEST_OTHER_VM(ZMountPoint, preferred_hugetlbfs_order) {
  ZMountPointTest::test("hugetlbfs", ZPreferredHugetlbfsMountpoints[0], ZPreferredHugetlbfsMountpoints[1]);
}

#endif // LINUX
