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
 */

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "zunittest.hpp"

using namespace testing;

#ifdef ASSERT

class ZNUMATest : public ZTest {
protected:
  const uint32_t nodes = 4;

  uint32_t _original_count;
  uint     _original_ZFakeNUMA;

public:
  virtual void SetUp() {
    _original_count = ZNUMA::_count;
    _original_ZFakeNUMA = ZFakeNUMA;

    // Setup number of NUMA nodes through faking
    ZFakeNUMA = nodes;
    ZNUMA::_count = nodes;
  }

  virtual void TearDown() {
    ZNUMA::_count = _original_count;
    ZFakeNUMA = _original_ZFakeNUMA;
  }
};

TEST_F(ZNUMATest, calculate_share) {
  {
    // Test even spread
    const size_t total = nodes * ZGranuleSize;

    for (uint32_t numa_id = 0; numa_id < nodes; ++numa_id) {
      EXPECT_EQ(ZNUMA::calculate_share(numa_id, total), ZGranuleSize);
    }
  }

  {
    // Test not enough for every node (WITHOUT ignore_count)
    const size_t total = (nodes - 1) * ZGranuleSize;

    for (uint32_t numa_id = 0; numa_id < (nodes - 1); ++numa_id) {
      EXPECT_EQ(ZNUMA::calculate_share(numa_id, total), ZGranuleSize);
    }
    EXPECT_EQ(ZNUMA::calculate_share(nodes - 1, total), (size_t)0);
  }

  {
    // Test not enough for every node (WITH ignore_count)
    const size_t ignore_count = 2;
    const size_t total = nodes * ZGranuleSize;

    for (uint32_t numa_id = 0; numa_id < (nodes - ignore_count); ++numa_id) {
      EXPECT_EQ(ZNUMA::calculate_share(numa_id, total, ZGranuleSize, ignore_count), nodes * ZGranuleSize / (nodes - ignore_count));
    }
  }

  {
    // Test no size
    const size_t total = 0;

    for (uint32_t numa_id = 0; numa_id < (nodes - 1); ++numa_id) {
      EXPECT_EQ(ZNUMA::calculate_share(numa_id, total), (size_t)0);
    }
  }

  {
    // Test one more than even
    const size_t total = (nodes + 1) * ZGranuleSize;

    EXPECT_EQ(ZNUMA::calculate_share(0, total), ZGranuleSize * 2);
    for (uint32_t numa_id = 1; numa_id < nodes; ++numa_id) {
      EXPECT_EQ(ZNUMA::calculate_share(numa_id, total), ZGranuleSize);
    }
  }

  {
    // Test one less than even
    const size_t total = (nodes * 2 - 1) * ZGranuleSize;

    for (uint32_t numa_id = 0; numa_id < (nodes - 1); ++numa_id) {
      EXPECT_EQ(ZNUMA::calculate_share(numa_id, total), 2 * ZGranuleSize);
    }
    EXPECT_EQ(ZNUMA::calculate_share(nodes - 1, total), ZGranuleSize);
  }
}

#endif // ASSERT
