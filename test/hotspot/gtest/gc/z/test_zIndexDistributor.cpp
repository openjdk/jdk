/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zIndexDistributor.inline.hpp"
#include "unittest.hpp"

class ZIndexDistributorTest : public ::testing::Test {
protected:
  static void test_claim_tree_claim_level_size() {
    // max_index: 16, 16, 16, rest
    // claim level: 1, 16, 16 * 16, 16 * 16 * 16
    ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_size(0), 1);
    ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_size(1), 16);
    ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_size(2), 16 * 16);
    ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_size(3), 16 * 16 * 16);
  }

  static void test_claim_tree_claim_level_end_index() {
    // First level is padded
    const int first_level_end = int(ZCacheLineSize / sizeof(int));
    ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_end_index(0), first_level_end);
    ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_end_index(1), first_level_end + 16);
    ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_end_index(2), first_level_end + 16 + 16 * 16);
    ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_end_index(3), first_level_end + 16 + 16 * 16 + 16 * 16 * 16);
  }

  static void test_claim_tree_claim_index() {
    // First level should always give index 0
    {
      int indices[4] = {0, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 0), 0);
    }
    {
      int indices[4] = {1, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 0), 0);
    }
    {
      int indices[4] = {15, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 0), 0);
    }
    {
      int indices[4] = {16, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 0), 0);
    }

    // Second level should depend on first claimed index

    // Second-level start after first-level padding
    const int second_level_start = int(ZCacheLineSize / sizeof(int));

    {
      int indices[4] = {0, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 1), second_level_start);
    }
    {
      int indices[4] = {1, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 1), second_level_start + 1);
    }
    {
      int indices[4] = {15, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 1), second_level_start + 15);
    }

    // Third level

    const int third_level_start = second_level_start + 16;

    {
      int indices[4] = {0, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 2), third_level_start);
    }
    {
      int indices[4] = {1, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 2), third_level_start + 1 * 16);
    }
    {
      int indices[4] = {15, 0, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 2), third_level_start + 15 * 16);
    }
    {
      int indices[4] = {1, 2, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 2), third_level_start + 1 * 16 + 2);
    }
    {
      int indices[4] = {15, 14, 0, 0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_index(indices, 2), third_level_start + 15 * 16 + 14);
    }

  }

  static void test_claim_tree_claim_level_index() {
    {
      int indices[4] = {0,0,0,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 1), 0);
    }
    {
      int indices[4] = {1,0,0,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 1), 1);
    }

    {
      int indices[4] = {0,0,0,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 2), 0);
    }
    {
      int indices[4] = {1,0,0,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 2), 1 * 16);
    }
    {
      int indices[4] = {2,0,0,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 2), 2 * 16);
    }
    {
      int indices[4] = {2,1,0,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 2), 2 * 16 + 1);
    }

    {
      int indices[4] = {0,0,0,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 3), 0);
    }
    {
      int indices[4] = {1,0,0,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 3), 1 * 16 * 16);
    }
    {
      int indices[4] = {1,2,0,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 3), 1 * 16 * 16 + 2 * 16);
    }
    {
      int indices[4] = {1,2,1,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 3), 1 * 16 * 16 + 2 * 16 + 1);
    }
    {
      int indices[4] = {1,2,3,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 3), 1 * 16 * 16 + 2 * 16 + 3);
    }
    {
      int indices[4] = {1,2,3,0};
      ASSERT_EQ(ZIndexDistributorClaimTree::claim_level_index(indices, 2), 1 * 16 + 2);
    }
  }
};

TEST_F(ZIndexDistributorTest, test_claim_tree_claim_level_size) {
  test_claim_tree_claim_level_size();
}

TEST_F(ZIndexDistributorTest, test_claim_tree_claim_level_end_index) {
  test_claim_tree_claim_level_end_index();
}

TEST_F(ZIndexDistributorTest, test_claim_tree_claim_level_index) {
  test_claim_tree_claim_level_index();
}

TEST_F(ZIndexDistributorTest, test_claim_tree_claim_index) {
  test_claim_tree_claim_index();
}
