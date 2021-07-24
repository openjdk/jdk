/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/arena.hpp"
#include "unittest.hpp"

TEST_VM(Arena, mixed_alignment_allocation) {
  // Test that mixed alignment allocations work and provide allocations with the correct
  // alignment
  Arena ar(mtTest);
  void* p1 = ar.AmallocWords(BytesPerWord);
  void* p2 = ar.Amalloc(BytesPerLong);
  ASSERT_TRUE(is_aligned(p1, BytesPerWord));
  ASSERT_TRUE(is_aligned(p2, BytesPerLong));
}

TEST_VM(Arena, Arena_with_crooked_initial_size) {
  // Test that an arena with a crooked, not 64-bit aligned initial size
  // works
  Arena ar(mtTest, 4097);
  void* p1 = ar.AmallocWords(BytesPerWord);
  void* p2 = ar.Amalloc(BytesPerLong);
  ASSERT_TRUE(is_aligned(p1, BytesPerWord));
  ASSERT_TRUE(is_aligned(p2, BytesPerLong));
}
