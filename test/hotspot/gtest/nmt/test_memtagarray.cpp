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

#include "nmt/memTag.hpp"
#include "nmt/mallocTracker.hpp"
#include "unittest.hpp"

class NMTMemTagArrayTest : public testing::Test {
public:
  using MTArray = MallocMemorySnapshot::MemTagArray;
};

TEST_VM_F(NMTMemTagArrayTest, AllocatingTagTest) {
  { // Allocate tags in order
    MTArray mta;
    ASSERT_TRUE(mta.is_valid()) << "must";
    EXPECT_EQ(0, mta.number_of_tags_allocated());
    for (int i = 0; i < mt_number_of_tags; i++) {
      mta.at((MemTag)i);
    }
    EXPECT_EQ(mt_number_of_tags, mta.number_of_tags_allocated());
  }

  { // Allocating a tag in the middle also allocates all preceding tags.
    MTArray mta;
    ASSERT_TRUE(mta.is_valid()) << "must";
    EXPECT_EQ(0, (int)mta.number_of_tags_allocated());

    mta.at(mtMetaspace);
    EXPECT_EQ((int)mtMetaspace + 1, (int)mta.number_of_tags_allocated());
  }
}
