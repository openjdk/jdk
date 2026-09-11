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
 *
 */

#include "nmt/memTag.hpp"
#include "nmt/vmatree.hpp"
#include "unittest.hpp"


// The SummaryDiff is seldom used with a large number of tags
// so test that separately.
TEST(NMTSummaryDiffTest, WorksForLargeTagCount) {
  VMATree::SummaryDiff d;
  for (int i = 0; i < std::numeric_limits<std::underlying_type_t<MemTag>>::max(); i++) {
    VMATree::SingleDiff& sd = d.tag(i);
    sd.reserve = i;
  }
  for (int i = 0; i < std::numeric_limits<std::underlying_type_t<MemTag>>::max(); i++) {
    VMATree::SingleDiff& sd = d.tag(i);
    EXPECT_EQ(i, sd.reserve);
  }
}
