/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "services/mallocHeader.inline.hpp"
#include "services/virtualMemoryView.hpp"
#include "nmtMemoryViewFixture.hpp"

#include "unittest.hpp"
#include "testutils.hpp"

// Check the results of VirtualMemoryView.:overlap.
// The test fixture wraps these APIs, we're constructing
// ranges R{start, end} encoding a range [start, end).
TEST_F(NmtVirtualMemoryViewTest, OverlappingRanges) {
  OutR result;
  result = overlap(R{0, 1}, R{1,2});
  ASSERT_TRUE(result.result == OverlappingResult::NoOverlap);
  ASSERT_TRUE(result.len == 0);

  result = overlap(R{0, 1}, R{0,2});
  ASSERT_TRUE(result.result == OverlappingResult::EntirelyEnclosed);
  ASSERT_TRUE(result.len == 0);

  result = overlap(R{0, 100}, R{50, 75});
  ASSERT_TRUE(result.result == OverlappingResult::SplitInMiddle);
  ASSERT_TRUE(result.len == 2);

  result = overlap(R{0, 100}, R{50, 100});
  ASSERT_TRUE(result.result == OverlappingResult::ShortenedFromRight);
  ASSERT_TRUE(result.len == 1);

  result = overlap(R{0, 100}, R{0, 50});
  ASSERT_TRUE(result.result == OverlappingResult::ShortenedFromLeft);
  ASSERT_TRUE(result.len == 1);
}

TEST_VM_F(NmtVirtualMemoryViewTest, ReservingMemoryInSpace) {
}

TEST_VM_F(NmtVirtualMemoryViewTest, CommittingMemoryInSpace) {
}
