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

#ifdef AIX

#include "runtime/os.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"

// On Aix, when using shmget() in os::attempt_reserve_memory_at() we should fail with attach
// attempts not aligned to shmget() segment boundaries (256m)
// But shmget() is only used in cases we want to have 64K pages and mmap() does not provide it.
TEST_VM(os_aix, aix_reserve_at_non_shmlba_aligned_address) {
  if (os::vm_page_size() != 4*K && !os::Aix::supports_64K_mmap_pages()) {
    // With this condition true shmget() is used inside
    char* p = os::attempt_reserve_memory_at((char*)0x1f00000, M, mtTest);
    ASSERT_EQ(p, nullptr); // should have failed
    p = os::attempt_reserve_memory_at((char*)((64 * G) + M), M, mtTest);
    ASSERT_EQ(p, nullptr); // should have failed
  }
}

#endif // AIX
