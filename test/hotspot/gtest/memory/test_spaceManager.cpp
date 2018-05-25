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
 */

#include "precompiled.hpp"
#include "memory/metaspace/spaceManager.hpp"

using metaspace::SpaceManager;

// The test function is only available in debug builds
#ifdef ASSERT

#include "unittest.hpp"


static void test_adjust_initial_chunk_size(bool is_class) {
  const size_t smallest = SpaceManager::smallest_chunk_size(is_class);
  const size_t normal   = SpaceManager::small_chunk_size(is_class);
  const size_t medium   = SpaceManager::medium_chunk_size(is_class);

#define do_test(value, expected, is_class_value)                                 \
    do {                                                                         \
      size_t v = value;                                                          \
      size_t e = expected;                                                       \
      assert(SpaceManager::adjust_initial_chunk_size(v, (is_class_value)) == e,  \
             "Expected: " SIZE_FORMAT " got: " SIZE_FORMAT, e, v);               \
    } while (0)

    // Smallest (specialized)
    do_test(1,            smallest, is_class);
    do_test(smallest - 1, smallest, is_class);
    do_test(smallest,     smallest, is_class);

    // Small
    do_test(smallest + 1, normal, is_class);
    do_test(normal - 1,   normal, is_class);
    do_test(normal,       normal, is_class);

    // Medium
    do_test(normal + 1, medium, is_class);
    do_test(medium - 1, medium, is_class);
    do_test(medium,     medium, is_class);

    // Humongous
    do_test(medium + 1, medium + 1, is_class);

#undef test_adjust_initial_chunk_size
}

TEST(SpaceManager, adjust_initial_chunk_size) {
  test_adjust_initial_chunk_size(true);
  test_adjust_initial_chunk_size(false);
}

#endif // ASSERT
