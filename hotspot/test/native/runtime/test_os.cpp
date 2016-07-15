/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

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

#ifdef ASSERT
TEST_VM_ASSERT_MSG(os, page_size_for_region_with_zero_min_pages, "sanity") {
  size_t region_size = 16 * os::vm_page_size();
  os::page_size_for_region_aligned(region_size, 0); // should assert
}
#endif
