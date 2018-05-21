/*
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"

// The test function is only available in debug builds
#ifdef ASSERT

#include "unittest.hpp"

using namespace metaspace;

TEST(ChunkManager, list_index) {

  // Test previous bug where a query for a humongous class metachunk,
  // incorrectly matched the non-class medium metachunk size.
  {
    ChunkManager manager(true);

    ASSERT_TRUE(MediumChunk > ClassMediumChunk) << "Precondition for test";

    ChunkIndex index = manager.list_index(MediumChunk);

    ASSERT_TRUE(index == HumongousIndex) <<
        "Requested size is larger than ClassMediumChunk,"
        " so should return HumongousIndex. Got index: " << index;
  }

  // Check the specified sizes as well.
  {
    ChunkManager manager(true);
    ASSERT_TRUE(manager.list_index(ClassSpecializedChunk) == SpecializedIndex);
    ASSERT_TRUE(manager.list_index(ClassSmallChunk) == SmallIndex);
    ASSERT_TRUE(manager.list_index(ClassMediumChunk) == MediumIndex);
    ASSERT_TRUE(manager.list_index(ClassMediumChunk + ClassSpecializedChunk) == HumongousIndex);
  }
  {
    ChunkManager manager(false);
    ASSERT_TRUE(manager.list_index(SpecializedChunk) == SpecializedIndex);
    ASSERT_TRUE(manager.list_index(SmallChunk) == SmallIndex);
    ASSERT_TRUE(manager.list_index(MediumChunk) == MediumIndex);
    ASSERT_TRUE(manager.list_index(MediumChunk + SpecializedChunk) == HumongousIndex);
  }

}

#endif // ASSERT
