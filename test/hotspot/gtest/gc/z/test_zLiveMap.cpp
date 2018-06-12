/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL.  Use is subject to license terms.
 */

#include "precompiled.hpp"
#include "gc/z/zLiveMap.inline.hpp"
#include "unittest.hpp"

class ZLiveMapTest : public ::testing::Test {
protected:
  static void strongly_live_for_large_zpage() {
    // Large ZPages only have room for one object.
    ZLiveMap livemap(1);

    bool inc_live;
    uintptr_t object = 0u;

    // Mark the object strong.
    livemap.set_atomic(object, false /* finalizable */, inc_live);

    // Check that both bits are in the same segment.
    ASSERT_EQ(livemap.index_to_segment(0), livemap.index_to_segment(1));

    // Check that the object was marked.
    ASSERT_TRUE(livemap.get(0));

    // Check that the object was strongly marked.
    ASSERT_TRUE(livemap.get(1));

    ASSERT_TRUE(inc_live);
  }
};

TEST_F(ZLiveMapTest, strongly_live_for_large_zpage) {
  strongly_live_for_large_zpage();
}
